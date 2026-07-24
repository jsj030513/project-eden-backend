package com.projecteden.vision.eden;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class EdenActivityRuleEngine {
	public List<EdenRuleCandidate> evaluate(EdenObservationFacts facts, float threshold) {
		List<EdenRuleCandidate> candidates = new ArrayList<>();
		add(candidates, "READING", "activity-reading-v1", facts.nearest(EdenObjectCode.PERSON, EdenObjectCode.BOOK));
		EdenObservationFacts.Pair device = best(facts.nearest(EdenObjectCode.PERSON, EdenObjectCode.LAPTOP), facts.nearest(EdenObjectCode.PERSON, EdenObjectCode.MONITOR));
		add(candidates, "WORK_OR_STUDY", "activity-work-or-study-v1", device);
		add(candidates, "CYCLING", "activity-cycling-v1", facts.nearest(EdenObjectCode.PERSON, EdenObjectCode.BICYCLE));
		EdenObservationFacts.Pair food = facts.nearestFamily(EdenObjectCode.PERSON, EdenObjectFamily.FOOD_AND_DRINK);
		if (food != null && facts.has(EdenObjectCode.TABLE)) add(candidates, "EATING_OR_CAFE", "activity-eating-or-cafe-v1", food);
		// WALK intentionally has no rule: COCO detections alone do not provide reliable outdoor evidence.
		return candidates.stream().filter(candidate -> candidate.confidence() >= threshold).sorted(order()).toList();
	}
	private EdenObservationFacts.Pair best(EdenObservationFacts.Pair first, EdenObservationFacts.Pair second) { if (first == null) return second; if (second == null) return first; return first.score() >= second.score() ? first : second; }
	private void add(List<EdenRuleCandidate> results, String code, String ruleId, EdenObservationFacts.Pair pair) {
		if (pair == null) return;
		float confidence = Math.min(pair.first().source().confidence().value(), pair.second().source().confidence().value()) * pair.score();
		results.add(new EdenRuleCandidate(code, confidence, ruleId, List.of(pair.first().code().name(), pair.second().code().name(), "PROXIMITY")));
	}
	private Comparator<EdenRuleCandidate> order() { return Comparator.comparing(EdenRuleCandidate::confidence).reversed().thenComparing(EdenRuleCandidate::ruleId).thenComparing(EdenRuleCandidate::code); }
}
