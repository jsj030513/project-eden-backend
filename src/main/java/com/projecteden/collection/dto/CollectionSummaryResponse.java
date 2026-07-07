package com.projecteden.collection.dto;
import java.util.List;
public record CollectionSummaryResponse(int totalCollectableCount, long uniqueCollectedCount, double completionRate, List<CollectionResponse> collections) {}
