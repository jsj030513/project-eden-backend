package com.projecteden.imagenormalization;

import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Node;

import com.projecteden.memorytaxonomy.observation.UploadedImagePayload;

@Service
public class DefaultImageNormalizationService implements ImageNormalizationService {

	private static final Logger log = LoggerFactory.getLogger(DefaultImageNormalizationService.class);

	private final ImageFormatDetector imageFormatDetector;
	private final ImageNormalizationProperties properties;
	private final ImageOrientationTransformer orientationTransformer = new ImageOrientationTransformer();

	public DefaultImageNormalizationService(
			ImageFormatDetector imageFormatDetector,
			ImageNormalizationProperties properties) {
		this.imageFormatDetector = imageFormatDetector;
		this.properties = properties;
	}

	@Override
	public NormalizedImage normalize(UploadedImagePayload input) {
		if (!properties.isEnabled()) {
			throw new ImageNormalizationException(
					ImageNormalizationErrorCode.NORMALIZATION_FAILED,
					"이미지 정규화가 비활성화되어 있습니다.");
		}
		byte[] inputBytes = validateInput(input);
		DetectedImageFormat detected = imageFormatDetector.detect(input);
		if (detected.format() == ImageFormat.UNKNOWN) {
			throw failure(ImageNormalizationErrorCode.UNSUPPORTED_FORMAT, "지원하지 않는 이미지 형식입니다.");
		}
		if (detected.format().isDecodeDeferred()) {
			throw failure(ImageNormalizationErrorCode.DECODER_UNAVAILABLE, "현재 처리할 수 없는 이미지 형식입니다.");
		}

		try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(inputBytes))) {
			ImageReader reader = selectReader(stream, detected.format());
			try {
				// Frame probing requires random access for several JDK ImageIO readers.
				reader.setInput(stream, false, false);
				int originalWidth = reader.getWidth(0);
				int originalHeight = reader.getHeight(0);
				validateDimensions(originalWidth, originalHeight);
				validateFrames(reader, detected.format());
				int orientation = orientation(reader);
				BufferedImage decoded = reader.read(0, readParam(reader));
				if (decoded == null) {
					throw failure(ImageNormalizationErrorCode.CORRUPTED_IMAGE, "이미지를 읽을 수 없습니다.");
				}
				boolean alpha = decoded.getColorModel().hasAlpha() && decoded.getTransparency() != Transparency.OPAQUE;
				boolean colorNormalized = !decoded.getColorModel().getColorSpace().isCS_sRGB();
				BufferedImage standard = toStandardSrgb(decoded, alpha);
				BufferedImage oriented = applyOrientation(standard, orientation, alpha);
				boolean orientationApplied = orientation != 1;
				BufferedImage resized = resize(oriented, alpha);
				boolean wasResized = resized.getWidth() != oriented.getWidth() || resized.getHeight() != oriented.getHeight();
				ImageFormat outputFormat = alpha ? ImageFormat.PNG : ImageFormat.JPEG;
				byte[] output = encode(resized, outputFormat);
				validateOutputSize(output.length);
				return new NormalizedImage(
						output,
						outputFormat.getContentType(),
						outputFormat,
						resized.getWidth(),
						resized.getHeight(),
						detected.format(),
						originalWidth,
						originalHeight,
						orientationApplied || wasResized || colorNormalized || outputFormat != detected.format(),
						orientationApplied,
						wasResized,
						colorNormalized,
						alpha,
						true,
						sha256(output));
			} finally {
				reader.dispose();
			}
		} catch (ImageNormalizationException exception) {
			throw exception;
		} catch (IOException exception) {
			throw new ImageNormalizationException(
					ImageNormalizationErrorCode.CORRUPTED_IMAGE,
					"이미지를 읽을 수 없습니다.",
					exception);
		} catch (RuntimeException exception) {
			throw new ImageNormalizationException(
					ImageNormalizationErrorCode.NORMALIZATION_FAILED,
					"이미지를 정규화할 수 없습니다.",
					exception);
		}
	}

	private byte[] validateInput(UploadedImagePayload input) {
		if (input == null || !input.hasBytes()) {
			throw failure(ImageNormalizationErrorCode.EMPTY_INPUT, "이미지 파일이 필요합니다.");
		}
		byte[] bytes = input.bytes();
		if (input.size() > properties.getEncodedMaxBytes() || bytes.length > properties.getEncodedMaxBytes()) {
			throw failure(ImageNormalizationErrorCode.ENCODED_SIZE_EXCEEDED, "이미지 파일 크기가 제한을 초과했습니다.");
		}
		return bytes;
	}

	private ImageReader selectReader(ImageInputStream stream, ImageFormat expected) throws IOException {
		Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
		if (!readers.hasNext()) {
			throw failure(ImageNormalizationErrorCode.DECODER_UNAVAILABLE, "이미지 decoder를 찾을 수 없습니다.");
		}
		ImageReader reader = readers.next();
		if (expected == ImageFormat.WEBP || expected == ImageFormat.TIFF) {
			log.debug("Image normalization reader selected format={} reader={}", expected, reader.getClass().getSimpleName());
		}
		return reader;
	}

	private ImageReadParam readParam(ImageReader reader) {
		return reader.getDefaultReadParam();
	}

	private void validateDimensions(int width, int height) {
		if (width <= 0 || height <= 0) {
			throw failure(ImageNormalizationErrorCode.CORRUPTED_IMAGE, "이미지 크기가 올바르지 않습니다.");
		}
		if (width > properties.getMaxWidth() || height > properties.getMaxHeight()) {
			throw failure(ImageNormalizationErrorCode.DIMENSION_EXCEEDED, "이미지 가로 또는 세로 크기가 제한을 초과했습니다.");
		}
		long pixels = (long) width * height;
		if (pixels > properties.getMaxPixels()) {
			throw failure(ImageNormalizationErrorCode.PIXEL_LIMIT_EXCEEDED, "이미지 픽셀 수가 제한을 초과했습니다.");
		}
	}

	private void validateFrames(ImageReader reader, ImageFormat format) throws IOException {
		if (format != ImageFormat.GIF && format != ImageFormat.WEBP && format != ImageFormat.TIFF) {
			return;
		}
		int frames = reader.getNumImages(true);
		if (frames > properties.getMaxFramesToProbe()) {
			throw failure(ImageNormalizationErrorCode.FRAME_LIMIT_EXCEEDED, "이미지 프레임 수가 제한을 초과했습니다.");
		}
	}

	private int orientation(ImageReader reader) {
		try {
			IIOMetadata metadata = reader.getImageMetadata(0);
			if (metadata == null || !metadata.isStandardMetadataFormatSupported()) {
				return 1;
			}
			return orientationFromNode(metadata.getAsTree("javax_imageio_1.0"));
		} catch (Exception exception) {
			log.warn("Image orientation metadata could not be read; treating as normal. reader={}",
					reader.getClass().getSimpleName());
			return 1;
		}
	}

	private int orientationFromNode(Node node) {
		if (node == null) {
			return 1;
		}
		if ("ImageOrientation".equals(node.getNodeName()) && node.getAttributes() != null) {
			Node value = node.getAttributes().getNamedItem("value");
			return value == null ? 1 : orientation(value.getNodeValue());
		}
		for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
			int orientation = orientationFromNode(child);
			if (orientation != 1) {
				return orientation;
			}
		}
		return 1;
	}

	private int orientation(String value) {
		return switch (value) {
			case "FlipH" -> 2;
			case "Rotate180" -> 3;
			case "FlipV" -> 4;
			case "FlipHRotate90" -> 5;
			case "Rotate90" -> 6;
			case "FlipVRotate90" -> 7;
			case "Rotate270" -> 8;
			default -> 1;
		};
	}

	private BufferedImage toStandardSrgb(BufferedImage source, boolean alpha) {
		try {
			int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
			BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), type);
			for (int y = 0; y < source.getHeight(); y++) {
				for (int x = 0; x < source.getWidth(); x++) {
					// BufferedImage#getRGB returns the default sRGB representation.
					target.setRGB(x, y, source.getRGB(x, y));
				}
			}
			return target;
		} catch (RuntimeException exception) {
			throw new ImageNormalizationException(
					ImageNormalizationErrorCode.COLOR_CONVERSION_FAILED,
					"이미지 색상 정보를 변환할 수 없습니다.", exception);
		}
	}

	private BufferedImage applyOrientation(BufferedImage source, int orientation, boolean alpha) {
		if (orientation == 1) {
			return source;
		}
		try {
			return orientationTransformer.transform(source, orientation, alpha);
		} catch (RuntimeException exception) {
			throw new ImageNormalizationException(
					ImageNormalizationErrorCode.ORIENTATION_FAILED,
					"이미지 방향을 보정할 수 없습니다.", exception);
		}
	}

	private BufferedImage resize(BufferedImage source, boolean alpha) {
		int width = source.getWidth();
		int height = source.getHeight();
		double scale = Math.min(1d, Math.min(
				(double) properties.getMaxOutputWidth() / width,
				(double) properties.getMaxOutputHeight() / height));
		if (scale >= 1d) {
			return source;
		}
		try {
			int targetWidth = Math.max(1, (int) Math.round(width * scale));
			int targetHeight = Math.max(1, (int) Math.round(height * scale));
			BufferedImage target = new BufferedImage(targetWidth, targetHeight,
					alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
			for (int y = 0; y < targetHeight; y++) {
				int sourceY = Math.min(height - 1, (int) ((long) y * height / targetHeight));
				for (int x = 0; x < targetWidth; x++) {
					int sourceX = Math.min(width - 1, (int) ((long) x * width / targetWidth));
					target.setRGB(x, y, source.getRGB(sourceX, sourceY));
				}
			}
			return target;
		} catch (RuntimeException exception) {
			throw new ImageNormalizationException(
					ImageNormalizationErrorCode.RESIZE_FAILED,
					"이미지 크기를 조정할 수 없습니다.", exception);
		}
	}

	private byte[] encode(BufferedImage image, ImageFormat outputFormat) {
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			if (outputFormat == ImageFormat.PNG) {
				if (!ImageIO.write(image, "png", output)) {
					throw failure(ImageNormalizationErrorCode.ENCODING_FAILED, "PNG encoder를 찾을 수 없습니다.");
				}
				return output.toByteArray();
			}
			Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
			if (!writers.hasNext()) {
				throw failure(ImageNormalizationErrorCode.ENCODING_FAILED, "JPEG encoder를 찾을 수 없습니다.");
			}
			ImageWriter writer = writers.next();
			try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
				writer.setOutput(imageOutput);
				ImageWriteParam parameters = writer.getDefaultWriteParam();
				if (parameters.canWriteCompressed()) {
					parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
					parameters.setCompressionQuality(properties.getJpegQuality());
				}
				writer.write(null, new IIOImage(image, null, null), parameters);
				return output.toByteArray();
			} finally {
				writer.dispose();
			}
		} catch (ImageNormalizationException exception) {
			throw exception;
		} catch (IOException | RuntimeException exception) {
			throw new ImageNormalizationException(
					ImageNormalizationErrorCode.ENCODING_FAILED,
					"이미지를 저장할 수 없습니다.", exception);
		}
	}

	private void validateOutputSize(int outputSize) {
		long limit = Math.min(properties.getOutputMaxBytes(), properties.getProviderMaxBytes());
		if (outputSize > limit) {
			throw failure(ImageNormalizationErrorCode.OUTPUT_SIZE_EXCEEDED, "정규화된 이미지 크기가 제한을 초과했습니다.");
		}
	}

	private String sha256(byte[] bytes) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder builder = new StringBuilder(hash.length * 2);
			for (byte value : hash) {
				builder.append(String.format("%02x", value));
			}
			return builder.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new ImageNormalizationException(
					ImageNormalizationErrorCode.NORMALIZATION_FAILED,
					"이미지 checksum을 만들 수 없습니다.", exception);
		}
	}

	private ImageNormalizationException failure(ImageNormalizationErrorCode code, String message) {
		return new ImageNormalizationException(code, message);
	}
}
