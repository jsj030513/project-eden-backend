package com.projecteden.memorytaxonomy.evaluation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

@Component
public class ImageEvaluationManifestReader {

	private final ObjectMapper objectMapper;

	public ImageEvaluationManifestReader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public List<ImageEvaluationCase> read(Path manifestPath, int maxCases) {
		try {
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
			JsonNode root = mapper.readTree(manifestPath.toFile());
			int version = root.isArray() ? 1 : root.path("version").asInt(1);
			JsonNode caseNodes = root.isArray() ? root : root.path("cases");
			List<ImageEvaluationCase> cases = mapper.convertValue(caseNodes, new TypeReference<>() {});
			cases = cases.stream().map(item -> item.manifestVersion() == null ? new ImageEvaluationCase(item.caseId(), item.imagePath(), item.expectedPrimary(), item.expectedSecondary(), item.expectedTags(), item.expectedRecognized(), item.notes(), version, item.enabled(), item.expectedObjects(), item.expectedActivities(), item.expectedRelationships(), item.expectedFallback()) : item).toList();
			validate(cases, maxCases);
			return cases.stream()
					.limit(maxCases)
					.toList();
		} catch (IOException ex) {
			throw new IllegalArgumentException("이미지 평가 manifest를 읽을 수 없습니다.", ex);
		}
	}

	private void validate(List<ImageEvaluationCase> cases, int maxCases) {
		if (cases == null) {
			throw new IllegalArgumentException("이미지 평가 manifest가 비어 있습니다.");
		}
		if (maxCases <= 0) {
			throw new IllegalArgumentException("이미지 평가 maxCases는 1 이상이어야 합니다.");
		}
		Set<String> caseIds = new HashSet<>();
		for (ImageEvaluationCase evaluationCase : cases) {
			if (evaluationCase.caseId() == null || evaluationCase.caseId().isBlank()) {
				throw new IllegalArgumentException("이미지 평가 caseId는 필수입니다.");
			}
			if (!caseIds.add(evaluationCase.caseId())) {
				throw new IllegalArgumentException("이미지 평가 caseId가 중복되었습니다.");
			}
			if (evaluationCase.isEnabled() && (evaluationCase.imagePath() == null || evaluationCase.imagePath().isBlank())) {
				throw new IllegalArgumentException("이미지 평가 imagePath는 필수입니다.");
			}
		}
	}
}
