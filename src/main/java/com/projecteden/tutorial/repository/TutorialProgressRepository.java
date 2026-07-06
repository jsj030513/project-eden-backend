package com.projecteden.tutorial.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.tutorial.domain.TutorialProgress;

public interface TutorialProgressRepository extends JpaRepository<TutorialProgress, Long> {

	Optional<TutorialProgress> findByCharacterId(Long characterId);

	boolean existsByCharacterId(Long characterId);
}
