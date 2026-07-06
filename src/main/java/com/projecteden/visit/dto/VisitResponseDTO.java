package com.projecteden.visit.dto;
import java.time.LocalDateTime;
public record VisitResponseDTO(Long visitId, Long ownerId, String ownerNickname, String islandName, String currentSeason, String representativeCreature, String representativeIsland, LocalDateTime visitedAt) {}
