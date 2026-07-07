package com.projecteden.collection.dto;
import java.time.LocalDateTime;
import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.collection.domain.Rarity;
public record CollectionResponse(Long id, RecognizedObject recognizedObject, Rarity rarity, int discoveredCount, LocalDateTime firstDiscoveredAt, LocalDateTime lastDiscoveredAt) {}
