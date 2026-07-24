package com.projecteden.vision.eden;

import java.util.List;

public record EdenRuleResult(List<EdenRuleCandidate> activities, List<EdenRuleCandidate> relationships) {
	public EdenRuleResult { activities = List.copyOf(activities); relationships = List.copyOf(relationships); }
}
