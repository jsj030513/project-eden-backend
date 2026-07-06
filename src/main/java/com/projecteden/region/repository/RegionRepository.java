package com.projecteden.region.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.region.domain.Region;
import com.projecteden.region.domain.RegionType;

public interface RegionRepository extends JpaRepository<Region, Long> {

	List<Region> findByWorldId(Long worldId);

	Optional<Region> findByWorldIdAndRegionType(Long worldId, RegionType regionType);
}
