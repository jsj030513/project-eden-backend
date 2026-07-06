package com.projecteden.tutorial.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.tutorial.domain.TutorialProgress;
import com.projecteden.tutorial.domain.TutorialStep;
import com.projecteden.tutorial.dto.TutorialResponse;
import com.projecteden.tutorial.repository.TutorialProgressRepository;

@Service
public class TutorialService {

	private final TutorialProgressRepository tutorialProgressRepository;
	private final CharacterRepository characterRepository;

	public TutorialService(
			TutorialProgressRepository tutorialProgressRepository,
			CharacterRepository characterRepository) {
		this.tutorialProgressRepository = tutorialProgressRepository;
		this.characterRepository = characterRepository;
	}

	@Transactional
	public TutorialResponse createTutorial(Long characterId) {
		if (tutorialProgressRepository.existsByCharacterId(characterId)) {
			throw new IllegalArgumentException("이미 튜토리얼이 존재합니다.");
		}

		Character character = characterRepository.findById(characterId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
		TutorialProgress progress = tutorialProgressRepository.save(TutorialProgress.create(character));
		return toResponse(progress);
	}

	@Transactional
	public TutorialResponse advanceTutorial(Long characterId, TutorialStep nextStep) {
		TutorialProgress progress = findProgress(characterId);
		progress.advanceTo(nextStep);
		return toResponse(progress);
	}

	@Transactional(readOnly = true)
	public TutorialResponse getMyTutorial(Long userId) {
		Character character = findCharacterByUserId(userId);
		return toResponse(findProgress(character.getId()));
	}

	@Transactional
	public TutorialResponse advanceMyTutorial(Long userId, TutorialStep nextStep) {
		Character character = findCharacterByUserId(userId);
		return advanceTutorial(character.getId(), nextStep);
	}

	private Character findCharacterByUserId(Long userId) {
		return characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
	}

	private TutorialProgress findProgress(Long characterId) {
		return tutorialProgressRepository.findByCharacterId(characterId)
				.orElseThrow(() -> new ResourceNotFoundException("튜토리얼을 찾을 수 없습니다."));
	}

	private TutorialResponse toResponse(TutorialProgress progress) {
		return new TutorialResponse(progress.getCurrentStep(), progress.isCompleted());
	}
}
