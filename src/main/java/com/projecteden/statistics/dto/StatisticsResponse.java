package com.projecteden.statistics.dto;
import java.time.LocalDateTime;
public record StatisticsResponse(long totalDiscoveries,long uniqueCollections,long totalAchievements,long totalTitles,LocalDateTime lastDiscoveryAt){}
