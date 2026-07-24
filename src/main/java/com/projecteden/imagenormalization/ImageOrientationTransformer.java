package com.projecteden.imagenormalization;

import java.awt.image.BufferedImage;

/** Applies the EXIF orientation value directly to image pixels. */
final class ImageOrientationTransformer {

	BufferedImage transform(BufferedImage source, int orientation, boolean alpha) {
		if (orientation == 1) {
			return source;
		}
		int width = source.getWidth();
		int height = source.getHeight();
		boolean swapsDimensions = orientation >= 5 && orientation <= 8;
		BufferedImage target = new BufferedImage(
				swapsDimensions ? height : width,
				swapsDimensions ? width : height,
				alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int[] point = targetPoint(x, y, width, height, orientation);
				target.setRGB(point[0], point[1], source.getRGB(x, y));
			}
		}
		return target;
	}

	private int[] targetPoint(int x, int y, int width, int height, int orientation) {
		return switch (orientation) {
			case 2 -> new int[] {width - 1 - x, y};
			case 3 -> new int[] {width - 1 - x, height - 1 - y};
			case 4 -> new int[] {x, height - 1 - y};
			case 5 -> new int[] {y, x};
			case 6 -> new int[] {height - 1 - y, x};
			case 7 -> new int[] {height - 1 - y, width - 1 - x};
			case 8 -> new int[] {y, width - 1 - x};
			default -> new int[] {x, y};
		};
	}
}
