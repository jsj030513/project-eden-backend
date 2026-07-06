package com.projecteden.plant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.plant.domain.Plant;

public interface PlantRepository extends JpaRepository<Plant, Long> {

	List<Plant> findByCharacterId(Long characterId);

	List<Plant> findByRegionWorldId(Long worldId);
}
