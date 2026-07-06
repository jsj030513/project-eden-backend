package com.projecteden.ai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.ai.domain.Recognition;

public interface RecognitionRepository extends JpaRepository<Recognition, Long> {

	Optional<Recognition> findByPhotoId(Long photoId);

	List<Recognition> findByPhotoCharacterId(Long characterId);
}
