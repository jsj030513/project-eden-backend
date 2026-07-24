package com.projecteden.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageDecoderCapabilityProofTests {

	@TempDir
	Path tempDir;

	@Test
	void jdkImageIoDecodesCoreRasterFormats() throws IOException {
		for (String format : List.of("jpeg", "png", "gif", "bmp")) {
			Path image = tempDir.resolve("core." + format);
			assertThat(ImageIO.write(testImage(), format, image.toFile())).isTrue();
			assertThat(ImageIO.read(image.toFile()))
					.as("ImageIO should decode the generated %s proof image", format)
					.isNotNull();
		}
	}

	@Test
	void twelveMonkeysPluginsRegisterReadersForTiffAndWebp() {
		assertThat(readerImplementationNames("tiff"))
				.anyMatch(name -> name.startsWith("com.twelvemonkeys."));
		assertThat(readerImplementationNames("webp"))
				.anyMatch(name -> name.startsWith("com.twelvemonkeys."));
	}

	@Test
	void heifFamilyHasNoReaderInTheCurrentPureJavaProofStack() {
		assertThat(readerImplementationNames("heic")).isEmpty();
		assertThat(readerImplementationNames("heif")).isEmpty();
		assertThat(readerImplementationNames("avif")).isEmpty();
		assertThat(readerImplementationNames("ico")).isEmpty();
	}

	private BufferedImage testImage() {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, Color.RED.getRGB());
		image.setRGB(1, 0, Color.GREEN.getRGB());
		image.setRGB(0, 1, Color.BLUE.getRGB());
		image.setRGB(1, 1, Color.WHITE.getRGB());
		return image;
	}

	private List<String> readerImplementationNames(String format) {
		List<String> names = new ArrayList<>();
		Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
		while (readers.hasNext()) {
			names.add(readers.next().getClass().getName());
		}
		return names;
	}
}
