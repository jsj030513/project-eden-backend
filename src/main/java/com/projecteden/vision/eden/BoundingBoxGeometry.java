package com.projecteden.vision.eden;

import com.projecteden.vision.detection.BoundingBox;

public final class BoundingBoxGeometry {
	private BoundingBoxGeometry() { }
	public static float iou(BoundingBox a, BoundingBox b) {
		float left = Math.max(a.x1(), b.x1()), top = Math.max(a.y1(), b.y1());
		float right = Math.min(a.x2(), b.x2()), bottom = Math.min(a.y2(), b.y2());
		float intersection = Math.max(0, right - left) * Math.max(0, bottom - top);
		float union = area(a) + area(b) - intersection;
		return union <= 0 ? 0 : intersection / union;
	}
	public static float normalizedCenterDistance(BoundingBox a, BoundingBox b) {
		float dx = centerX(a) - centerX(b), dy = centerY(a) - centerY(b);
		float distance = (float) Math.hypot(dx, dy);
		float diagonal = (float) Math.hypot(Math.max(a.x2(), b.x2()) - Math.min(a.x1(), b.x1()), Math.max(a.y2(), b.y2()) - Math.min(a.y1(), b.y1()));
		return diagonal == 0 ? Float.POSITIVE_INFINITY : distance / diagonal;
	}
	public static boolean nearOrOverlaps(BoundingBox a, BoundingBox b, float threshold) { return iou(a, b) > 0 || normalizedCenterDistance(a, b) <= threshold; }
	private static float area(BoundingBox box) { return (box.x2() - box.x1()) * (box.y2() - box.y1()); }
	private static float centerX(BoundingBox box) { return (box.x1() + box.x2()) / 2f; }
	private static float centerY(BoundingBox box) { return (box.y1() + box.y2()) / 2f; }
}
