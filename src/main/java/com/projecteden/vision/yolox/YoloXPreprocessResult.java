package com.projecteden.vision.yolox;

import java.util.Arrays;

public final class YoloXPreprocessResult {
	private final float[] tensorData;
	private final YoloXLetterboxTransform transform;
	public YoloXPreprocessResult(float[] tensorData, YoloXLetterboxTransform transform) {
		this.tensorData = Arrays.copyOf(tensorData, tensorData.length);
		this.transform = transform;
	}
	public float[] tensorData() { return Arrays.copyOf(tensorData, tensorData.length); }
	public YoloXLetterboxTransform transform() { return transform; }
}
