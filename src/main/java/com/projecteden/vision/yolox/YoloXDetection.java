package com.projecteden.vision.yolox;

public record YoloXDetection(int classIndex, String className, float confidence, float x1, float y1, float x2, float y2) {
}
