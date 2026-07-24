package com.projecteden.imagenormalization;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class ImageOrientationTransformerTests {

	private final ImageOrientationTransformer transformer = new ImageOrientationTransformer();

	@Test
	void appliesAllExifOrientationsWithoutLosingPixels() {
		for (int orientation = 1; orientation <= 8; orientation++) {
			BufferedImage transformed = transformer.transform(source(), orientation, false);
			if (orientation >= 5) {
				assertThat(transformed.getWidth()).isEqualTo(3);
				assertThat(transformed.getHeight()).isEqualTo(2);
			} else {
				assertThat(transformed.getWidth()).isEqualTo(2);
				assertThat(transformed.getHeight()).isEqualTo(3);
			}
			assertThat(colors(transformed)).containsExactlyInAnyOrder(
					Color.RED.getRGB(), Color.GREEN.getRGB(), Color.BLUE.getRGB(),
					Color.WHITE.getRGB(), Color.BLACK.getRGB(), Color.YELLOW.getRGB());
		}
	}

	@Test
	void rotatesOrientationSixClockwise() {
		BufferedImage transformed = transformer.transform(source(), 6, false);

		assertThat(transformed.getRGB(2, 0)).isEqualTo(Color.RED.getRGB());
		assertThat(transformed.getRGB(2, 1)).isEqualTo(Color.GREEN.getRGB());
		assertThat(transformed.getRGB(1, 0)).isEqualTo(Color.BLUE.getRGB());
	}

	private BufferedImage source() {
		BufferedImage image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, Color.RED.getRGB());
		image.setRGB(1, 0, Color.GREEN.getRGB());
		image.setRGB(0, 1, Color.BLUE.getRGB());
		image.setRGB(1, 1, Color.WHITE.getRGB());
		image.setRGB(0, 2, Color.BLACK.getRGB());
		image.setRGB(1, 2, Color.YELLOW.getRGB());
		return image;
	}

	private int[] colors(BufferedImage image) {
		int[] colors = new int[image.getWidth() * image.getHeight()];
		int index = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				colors[index++] = image.getRGB(x, y);
			}
		}
		return colors;
	}
}
