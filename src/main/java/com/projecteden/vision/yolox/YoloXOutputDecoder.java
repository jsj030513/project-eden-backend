package com.projecteden.vision.yolox;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.projecteden.vision.VisionRuntimeErrorCode;
import com.projecteden.vision.VisionRuntimeException;

@Component
public class YoloXOutputDecoder {
	private static final int[] STRIDES = {8,16,32};
	/**
	 * Diagnostic-only summary used by the local runtime. It never retains boxes,
	 * labels, image bytes, or raw model output.
	 */
	public float maxCandidateConfidence(float[][] rows) {
		if (rows == null) return 0f;
		float maximum = 0f;
		for (float[] row : rows) {
			if (row == null || row.length < 85 || !finite(row)) continue;
			for (int cls = 0; cls < 80; cls++) {
				float score = row[4] * row[5 + cls];
				if (Float.isFinite(score)) maximum = Math.max(maximum, score);
			}
		}
		return maximum;
	}

	public List<YoloXDetection> decode(float[][] rows, YoloXLetterboxTransform transform, float threshold, float nmsThreshold, int maxDetections) {
		if (rows == null || rows.length != 3549) throw new VisionRuntimeException(VisionRuntimeErrorCode.OUTPUT_CONTRACT_MISMATCH, "YOLOX 출력 후보 수가 올바르지 않습니다.", null);
		List<YoloXDetection> candidates = new ArrayList<>(); int index=0;
		for (int stride : STRIDES) { int grid=416/stride; for (int gy=0; gy<grid; gy++) for (int gx=0; gx<grid; gx++,index++) {
			float[] row=rows[index]; if(row.length<85 || !finite(row)) continue;
			float cx=(row[0]+gx)*stride, cy=(row[1]+gy)*stride, width=(float)Math.exp(row[2])*stride, height=(float)Math.exp(row[3])*stride;
			for(int cls=0; cls<80; cls++) { float score=row[4]*row[5+cls]; if(score<threshold || !Float.isFinite(score)) continue; candidates.add(restore(cls,score,cx,cy,width,height,transform)); }
		}}
		return YoloXNms.classAware(candidates,nmsThreshold,maxDetections);
	}
	private YoloXDetection restore(int cls,float score,float cx,float cy,float w,float h,YoloXLetterboxTransform t) { float x1=clamp((cx-w/2-t.paddingLeft())/t.scale(),0,t.originalWidth()), y1=clamp((cy-h/2-t.paddingTop())/t.scale(),0,t.originalHeight()), x2=clamp((cx+w/2-t.paddingLeft())/t.scale(),0,t.originalWidth()), y2=clamp((cy+h/2-t.paddingTop())/t.scale(),0,t.originalHeight()); return new YoloXDetection(cls,CocoClassLabels.nameOf(cls),score,x1,y1,x2,y2); }
	private boolean finite(float[] row) { for(float value:row) if(!Float.isFinite(value)) return false; return true; }
	private float clamp(float value,float min,float max) { return Math.max(min,Math.min(max,value)); }
}
