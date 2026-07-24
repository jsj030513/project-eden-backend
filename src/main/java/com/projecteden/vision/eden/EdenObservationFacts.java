package com.projecteden.vision.eden;

import java.util.Comparator;
import java.util.List;

public final class EdenObservationFacts {
	private final List<EdenMappedObject> mapped;
	private final float proximityThreshold;

	public EdenObservationFacts(List<EdenMappedObject> mapped, float proximityThreshold) {
		this.mapped = mapped == null ? List.of() : mapped.stream().sorted(Comparator
				.comparing((EdenMappedObject object) -> object.source().confidence().value()).reversed()
				.thenComparing(object -> object.code().name()).thenComparing(object -> object.source().boundingBox().x1()).thenComparing(object -> object.source().boundingBox().y1())).toList();
		this.proximityThreshold = proximityThreshold;
	}
	public List<EdenMappedObject> mapped() { return mapped; }
	public int count(EdenObjectCode code) { return (int) mapped.stream().filter(object -> object.code() == code).count(); }
	public boolean has(EdenObjectCode code) { return count(code) > 0; }
	public boolean hasFamily(EdenObjectFamily family) { return mapped.stream().anyMatch(object -> object.family() == family); }
	public List<EdenMappedObject> objects(EdenObjectCode code) { return mapped.stream().filter(object -> object.code() == code).toList(); }
	public List<EdenMappedObject> family(EdenObjectFamily family) { return mapped.stream().filter(object -> object.family() == family).toList(); }
	public boolean near(EdenMappedObject first, EdenMappedObject second) { return BoundingBoxGeometry.nearOrOverlaps(first.source().boundingBox(), second.source().boundingBox(), proximityThreshold); }
	public float proximityScore(EdenMappedObject first, EdenMappedObject second) {
		if (BoundingBoxGeometry.iou(first.source().boundingBox(), second.source().boundingBox()) > 0) return 1f;
		float distance = BoundingBoxGeometry.normalizedCenterDistance(first.source().boundingBox(), second.source().boundingBox());
		return distance > proximityThreshold ? 0f : Math.max(.75f, 1f - .25f * (distance / proximityThreshold));
	}
	public Pair nearest(EdenObjectCode first, EdenObjectCode second) {
		return objects(first).stream().flatMap(a -> objects(second).stream().map(b -> new Pair(a, b, proximityScore(a, b))))
				.filter(pair -> pair.score() > 0).max(Comparator.comparing(Pair::score)).orElse(null);
	}
	public Pair nearestFamily(EdenObjectCode first, EdenObjectFamily family) {
		return objects(first).stream().flatMap(a -> family(family).stream().map(b -> new Pair(a, b, proximityScore(a, b))))
				.filter(pair -> pair.score() > 0).max(Comparator.comparing(Pair::score)).orElse(null);
	}
	public record Pair(EdenMappedObject first, EdenMappedObject second, float score) { }
}
