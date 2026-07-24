package com.projecteden.vision.eden;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.projecteden.vision.detection.BoundingBox;
import com.projecteden.vision.detection.DetectionConfidence;
import com.projecteden.vision.detection.DetectionObject;
import com.projecteden.vision.yolox.CocoClassLabels;

class CocoToEdenObjectMapperTests {
	private final CocoToEdenObjectMapper mapper = new CocoToEdenObjectMapper();
	@Test void mapsCocoLabelsWithoutChangingDetectionConfidence() {
		var mapped = mapper.map(object("cell phone", .72f)).orElseThrow();
		assertThat(mapped.code()).isEqualTo(EdenObjectCode.PHONE);
		assertThat(mapped.family()).isEqualTo(EdenObjectFamily.DIGITAL_DEVICE);
		assertThat(mapped.source().confidence().value()).isEqualTo(.72f);
		assertThat(mapped.mappingVersion()).isEqualTo("eden-object-map-v1");
	}
	@Test void mapsEveryOfficialCocoLabelToAnExplicitEdenGroup() {
		assertThat(CocoClassLabels.all())
			.allSatisfy(label -> assertThat(mapper.map(object(label, .8f))).as(label).isPresent());
	}
	@Test void preservesUnknownDetectorLabelsAsAnExplicitFallback() {
		assertThat(mapper.map(object("future detector label", .8f)).orElseThrow().code())
			.isEqualTo(EdenObjectCode.UNKNOWN_OBJECT);
	}
	@Test void mapsTeddyBearAsASafeGenericObject() { assertThat(mapper.map(object("teddy bear", .72f)).orElseThrow().code()).isEqualTo(EdenObjectCode.TEDDY_BEAR); }
	@Test void mapsOrangeToExistingFruitRecognitionSignal() {
		var mapped = mapper.map(object("orange", .72f)).orElseThrow();
		assertThat(mapped.code()).isEqualTo(EdenObjectCode.FRUIT);
		assertThat(mapped.family()).isEqualTo(EdenObjectFamily.FOOD_AND_DRINK);
	}
	private DetectionObject object(String code, float confidence) { return new DetectionObject(code, new DetectionConfidence(confidence), new BoundingBox(0, 0, 10, 10)); }
}
