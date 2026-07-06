package com.projecteden.daily.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.daily.domain.DailyMission;

public interface DailyMissionRepository extends JpaRepository<DailyMission, Long> {

	Optional<DailyMission> findByCharacterIdAndMissionDate(Long characterId, LocalDate missionDate);
}
