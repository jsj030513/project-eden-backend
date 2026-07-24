package com.projecteden.vision.yolox;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.projecteden.imagenormalization.NormalizedImage;
import com.projecteden.vision.VisionRuntimeErrorCode;
import com.projecteden.vision.VisionRuntimeException;

@Component
public class YoloXImagePreprocessor {
	public static final int INPUT_WIDTH = 416;
	public static final int INPUT_HEIGHT = 416;
	private static final int CHANNELS = 3;
	private static final int PADDING_VALUE = 114;

	public YoloXPreprocessResult preprocess(NormalizedImage image) {
		return preprocess(image.bytes());
	}

	public YoloXPreprocessResult preprocess(byte[] imageBytes) {
		try {
			BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(imageBytes));
			if (decoded == null) throw new IOException("decoded image is null");
			return preprocess(decoded);
		} catch (IOException exception) {
			throw new VisionRuntimeException(VisionRuntimeErrorCode.PREPROCESSING_FAILED,
					"정규화된 이미지를 Vision 입력으로 변환할 수 없습니다.", exception);
		}
	}

	public YoloXPreprocessResult preprocess(BufferedImage image) {
		if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
			throw new VisionRuntimeException(VisionRuntimeErrorCode.PREPROCESSING_FAILED, "이미지 크기가 올바르지 않습니다.", null);
		}
		float scale = Math.min((float) INPUT_WIDTH / image.getWidth(), (float) INPUT_HEIGHT / image.getHeight());
		int resizedWidth = Math.max(1, Math.round(image.getWidth() * scale));
		int resizedHeight = Math.max(1, Math.round(image.getHeight() * scale));
		float[] data = new float[CHANNELS * INPUT_WIDTH * INPUT_HEIGHT];
		java.util.Arrays.fill(data, PADDING_VALUE);
		for (int y = 0; y < resizedHeight; y++) {
			float sourceY = ((y + 0.5f) / scale) - 0.5f;
			for (int x = 0; x < resizedWidth; x++) {
				float sourceX = ((x + 0.5f) / scale) - 0.5f;
				int rgb = bilinearRgb(image, sourceX, sourceY);
				int index = y * INPUT_WIDTH + x;
				// Official YOLOX ONNX demo obtains BGR pixels through cv2.imread, then CHW swap=(2,0,1).
				data[index] = rgb & 0xff;
				data[INPUT_WIDTH * INPUT_HEIGHT + index] = (rgb >>> 8) & 0xff;
				data[2 * INPUT_WIDTH * INPUT_HEIGHT + index] = (rgb >>> 16) & 0xff;
			}
		}
		return new YoloXPreprocessResult(data, new YoloXLetterboxTransform(
				image.getWidth(), image.getHeight(), resizedWidth, resizedHeight,
				INPUT_WIDTH, INPUT_HEIGHT, scale, 0, 0));
	}

	private int bilinearRgb(BufferedImage image, float x, float y) {
		int x0 = clamp((int) Math.floor(x), 0, image.getWidth() - 1);
		int y0 = clamp((int) Math.floor(y), 0, image.getHeight() - 1);
		int x1 = clamp(x0 + 1, 0, image.getWidth() - 1);
		int y1 = clamp(y0 + 1, 0, image.getHeight() - 1);
		float dx = Math.max(0, Math.min(1, x - x0));
		float dy = Math.max(0, Math.min(1, y - y0));
		return blend(image.getRGB(x0, y0), image.getRGB(x1, y0), image.getRGB(x0, y1), image.getRGB(x1, y1), dx, dy);
	}

	private int blend(int c00, int c10, int c01, int c11, float dx, float dy) {
		int r = interpolate((c00 >>> 16) & 255, (c10 >>> 16) & 255, (c01 >>> 16) & 255, (c11 >>> 16) & 255, dx, dy);
		int g = interpolate((c00 >>> 8) & 255, (c10 >>> 8) & 255, (c01 >>> 8) & 255, (c11 >>> 8) & 255, dx, dy);
		int b = interpolate(c00 & 255, c10 & 255, c01 & 255, c11 & 255, dx, dy);
		return (r << 16) | (g << 8) | b;
	}
	private int interpolate(int a, int b, int c, int d, float dx, float dy) { return Math.round((a + (b-a)*dx) * (1-dy) + (c + (d-c)*dx) * dy); }
	private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
