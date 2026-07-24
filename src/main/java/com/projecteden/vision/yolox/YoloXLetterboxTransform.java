package com.projecteden.vision.yolox;

public record YoloXLetterboxTransform(
		int originalWidth, int originalHeight, int resizedWidth, int resizedHeight,
		int inputWidth, int inputHeight, float scale, int paddingLeft, int paddingTop) {
}
