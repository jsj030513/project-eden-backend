package com.projecteden.resonance.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.resonance.domain.Resonance;

public interface ResonanceRepository extends JpaRepository<Resonance, Long> {

	boolean existsByRecognitionId(Long recognitionId);

	boolean existsByCharacterIdAndRecognizedObjectAndResonanceDate(
			Long characterId, RecognizedObject recognizedObject, LocalDate resonanceDate);

	long countByCharacterIdAndResonanceDate(Long characterId, LocalDate resonanceDate);

	List<Resonance> findByCharacterId(Long characterId);
}
