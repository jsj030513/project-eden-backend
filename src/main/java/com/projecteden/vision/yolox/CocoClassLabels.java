package com.projecteden.vision.yolox;

import java.util.List;

public final class CocoClassLabels {
	private static final List<String> LABELS = List.of("person","bicycle","car","motorcycle","airplane","bus","train","truck","boat","traffic light","fire hydrant","stop sign","parking meter","bench","bird","cat","dog","horse","sheep","cow","elephant","bear","zebra","giraffe","backpack","umbrella","handbag","tie","suitcase","frisbee","skis","snowboard","sports ball","kite","baseball bat","baseball glove","skateboard","surfboard","tennis racket","bottle","wine glass","cup","fork","knife","spoon","bowl","banana","apple","sandwich","orange","broccoli","carrot","hot dog","pizza","donut","cake","chair","couch","potted plant","bed","dining table","toilet","tv","laptop","mouse","remote","keyboard","cell phone","microwave","oven","toaster","sink","refrigerator","book","clock","vase","scissors","teddy bear","hair drier","toothbrush");
	private CocoClassLabels() { }
	public static String nameOf(int index) { return index >= 0 && index < LABELS.size() ? LABELS.get(index) : "unknown"; }
	public static int size() { return LABELS.size(); }
	public static List<String> all() { return LABELS; }
}
