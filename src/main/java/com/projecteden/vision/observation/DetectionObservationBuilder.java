package com.projecteden.vision.observation;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.projecteden.memorytaxonomy.observation.ImageObservation;
import com.projecteden.vision.config.VisionModelProperties;
import com.projecteden.vision.detection.DetectionResult;
import com.projecteden.vision.eden.CocoToEdenObjectMapper;
import com.projecteden.vision.eden.EdenActivityRuleEngine;
import com.projecteden.vision.eden.EdenObservationFacts;
import com.projecteden.vision.eden.EdenRelationshipRuleEngine;

@Component
public class DetectionObservationBuilder {
	public static final String PROVIDER = "LOCAL_YOLOX";

	private final DetectionSceneResolver sceneResolver;
	private final DetectionSubjectResolver subjectResolver;
	private final DetectionObjectResolver objectResolver;
	private final CocoToEdenObjectMapper objectMapper;
	private final EdenActivityRuleEngine activityRules;
	private final EdenRelationshipRuleEngine relationshipRules;
	private final VisionModelProperties properties;

	public DetectionObservationBuilder(DetectionSceneResolver sceneResolver, DetectionSubjectResolver subjectResolver, DetectionObjectResolver objectResolver) {
		this(sceneResolver, subjectResolver, objectResolver, new CocoToEdenObjectMapper(), new EdenActivityRuleEngine(), new EdenRelationshipRuleEngine(), new VisionModelProperties());
	}

	@Autowired
	public DetectionObservationBuilder(DetectionSceneResolver sceneResolver, DetectionSubjectResolver subjectResolver, DetectionObjectResolver objectResolver,
			CocoToEdenObjectMapper objectMapper, EdenActivityRuleEngine activityRules, EdenRelationshipRuleEngine relationshipRules, VisionModelProperties properties) {
		this.sceneResolver = sceneResolver;
		this.subjectResolver = subjectResolver;
		this.objectResolver = objectResolver;
		this.objectMapper = objectMapper;
		this.activityRules = activityRules;
		this.relationshipRules = relationshipRules;
		this.properties = properties;
	}

	public ImageObservation build(DetectionResult result) {
		if (result == null || result.isEmpty()) {
			// A valid image with no detector candidate is not a provider outage.  Keep
			// it as a truthful, broad memory so the upload can still become a safe
			// world expression without inventing an object name.
			return ImageObservation.generalMemory(PROVIDER, result == null ? "yolox-nano" : result.modelVersion());
		}
		var mapped = objectMapper.map(result);
		var facts = new EdenObservationFacts(mapped, properties.getRules().getProximityThreshold());
		var activities = activityRules.evaluate(facts, properties.getRules().getActivityThreshold()).stream().map(candidate -> candidate.code()).toList();
		var relationships = relationshipRules.evaluate(facts, properties.getRules().getRelationshipThreshold()).stream().map(candidate -> candidate.code()).toList();
		List<String> objects = mapped.stream().map(object -> object.code().name()).distinct().toList();
		return ImageObservation.recognized(subjectResolver.resolve(result), objects, sceneResolver.resolve(result), activities, relationships, List.of(), PROVIDER, result.modelVersion(), highestConfidence(result));
	}

	private BigDecimal highestConfidence(DetectionResult result) {
		float max = result.objects().stream().map(object -> object.confidence().value()).max(Float::compare).orElse(0f);
		return new BigDecimal(Float.toString(max));
	}
}
