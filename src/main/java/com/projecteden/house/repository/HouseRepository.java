package com.projecteden.house.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.house.domain.House;

public interface HouseRepository extends JpaRepository<House, Long> {

	Optional<House> findByWorldId(Long worldId);

	boolean existsByWorldId(Long worldId);
}
