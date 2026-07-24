package com.projecteden.memorytaxonomy.vision;
import java.util.List;
public record VisionProjectionCandidate(VisionProjectionSourceType sourceType, String sourceCode, String targetType, String targetCode, float confidence, String ruleId, List<String> evidence) { }
