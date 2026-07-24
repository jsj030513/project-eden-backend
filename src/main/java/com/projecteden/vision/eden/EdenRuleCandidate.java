package com.projecteden.vision.eden;

import java.util.List;

public record EdenRuleCandidate(String code, float confidence, String ruleId, List<String> evidence) { }
