package com.projecteden.vision.yolox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class YoloXNms {
	private YoloXNms() { }
	public static List<YoloXDetection> classAware(List<YoloXDetection> candidates, float threshold, int maxDetections) {
		List<YoloXDetection> ordered = candidates.stream().filter(YoloXNms::finite).sorted(order()).toList();
		List<YoloXDetection> selected = new ArrayList<>();
		for (YoloXDetection candidate : ordered) {
			if (selected.size() >= maxDetections) break;
			boolean overlaps = selected.stream().anyMatch(existing -> existing.classIndex() == candidate.classIndex() && iou(existing, candidate) > threshold);
			if (!overlaps) selected.add(candidate);
		}
		return List.copyOf(selected);
	}
	private static Comparator<YoloXDetection> order() { return Comparator.comparing(YoloXDetection::confidence).reversed().thenComparingInt(YoloXDetection::classIndex).thenComparing(YoloXDetection::x1).thenComparing(YoloXDetection::y1); }
	private static boolean finite(YoloXDetection detection) { return Float.isFinite(detection.confidence()) && Float.isFinite(detection.x1()) && Float.isFinite(detection.y1()) && Float.isFinite(detection.x2()) && Float.isFinite(detection.y2()) && detection.x2() > detection.x1() && detection.y2() > detection.y1(); }
	private static float iou(YoloXDetection a, YoloXDetection b) { float left=Math.max(a.x1(),b.x1()), top=Math.max(a.y1(),b.y1()), right=Math.min(a.x2(),b.x2()), bottom=Math.min(a.y2(),b.y2()); float intersection=Math.max(0,right-left)*Math.max(0,bottom-top); float union=(a.x2()-a.x1())*(a.y2()-a.y1())+(b.x2()-b.x1())*(b.y2()-b.y1())-intersection; return union<=0?0:intersection/union; }
}
