package com.projecteden.vision.eden;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class EdenRelationshipRuleEngine {
	public List<EdenRuleCandidate> evaluate(EdenObservationFacts facts, float threshold) {
		List<EdenRuleCandidate> candidates = new ArrayList<>();
		add(candidates, "PERSON_WITH_CAT", "relationship-person-cat-v1", facts.nearest(EdenObjectCode.PERSON, EdenObjectCode.CAT));
		add(candidates, "PERSON_WITH_DOG", "relationship-person-dog-v1", facts.nearest(EdenObjectCode.PERSON, EdenObjectCode.DOG));
		add(candidates, "PERSON_WITH_ANIMAL", "relationship-person-animal-v1", facts.nearestFamily(EdenObjectCode.PERSON, EdenObjectFamily.ANIMAL));
		add(candidates, "PERSON_WITH_BOOK", "relationship-person-book-v1", facts.nearest(EdenObjectCode.PERSON, EdenObjectCode.BOOK));
		EdenObservationFacts.Pair computer = best(facts.nearest(EdenObjectCode.PERSON, EdenObjectCode.LAPTOP), facts.nearest(EdenObjectCode.PERSON, EdenObjectCode.MONITOR));
		add(candidates, "PERSON_WITH_COMPUTER", "relationship-person-computer-v1", computer);
		add(candidates, "PERSON_WITH_BICYCLE", "relationship-person-bicycle-v1", facts.nearest(EdenObjectCode.PERSON, EdenObjectCode.BICYCLE));
		if (facts.count(EdenObjectCode.PERSON) >= 2) {
			List<EdenMappedObject> people = facts.objects(EdenObjectCode.PERSON);
			for (int i = 0; i < people.size(); i++) for (int j = i + 1; j < people.size(); j++) if (facts.near(people.get(i), people.get(j))) {
				float score = facts.proximityScore(people.get(i), people.get(j));
				candidates.add(new EdenRuleCandidate("MULTIPLE_PERSONS", Math.min(people.get(i).source().confidence().value(), people.get(j).source().confidence().value()) * score, "relationship-multiple-persons-v1", List.of("PERSON", "PERSON", "PROXIMITY")));
				break;
			}
		}
		return candidates.stream().filter(candidate -> candidate.confidence() >= threshold).sorted(Comparator.comparing(EdenRuleCandidate::confidence).reversed().thenComparing(EdenRuleCandidate::ruleId).thenComparing(EdenRuleCandidate::code)).toList();
	}
	private EdenObservationFacts.Pair best(EdenObservationFacts.Pair first, EdenObservationFacts.Pair second) { if (first == null) return second; if (second == null) return first; return first.score() >= second.score() ? first : second; }
	private void add(List<EdenRuleCandidate> results, String code, String ruleId, EdenObservationFacts.Pair pair) { if (pair != null) results.add(new EdenRuleCandidate(code, Math.min(pair.first().source().confidence().value(), pair.second().source().confidence().value()) * pair.score(), ruleId, List.of(pair.first().code().name(), pair.second().code().name(), "PROXIMITY"))); }
}
