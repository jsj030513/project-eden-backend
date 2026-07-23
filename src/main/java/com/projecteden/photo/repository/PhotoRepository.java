package com.projecteden.photo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.projecteden.photo.domain.Photo;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

	List<Photo> findByCharacterId(Long characterId);

	Optional<Photo> findByPlantId(Long plantId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select photo from Photo photo where photo.id = :id")
	Optional<Photo> findByIdForUpdate(@Param("id") Long id);
}
