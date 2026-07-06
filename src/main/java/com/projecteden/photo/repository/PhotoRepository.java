package com.projecteden.photo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.photo.domain.Photo;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

	List<Photo> findByCharacterId(Long characterId);

	Optional<Photo> findByPlantId(Long plantId);
}
