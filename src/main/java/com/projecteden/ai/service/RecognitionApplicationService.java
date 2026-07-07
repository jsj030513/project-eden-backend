package com.projecteden.ai.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.dto.RecognitionResponse;
import com.projecteden.ai.dto.RecognitionResult;
import com.projecteden.ai.repository.RecognitionRepository;
import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.evolution.domain.EvolutionSourceType;
import com.projecteden.evolution.service.EvolutionService;

@Service
public class RecognitionApplicationService {

	private final RecognitionRepository recognitionRepository;
	private final PhotoRepository photoRepository;
	private final CharacterRepository characterRepository;
	private final RecognitionService recognitionService;
	private final EvolutionService evolutionService;

	public RecognitionApplicationService(
			RecognitionRepository recognitionRepository,
			PhotoRepository photoRepository,
			CharacterRepository characterRepository,
			RecognitionService recognitionService,
			EvolutionService evolutionService) {
		this.recognitionRepository = recognitionRepository;
		this.photoRepository = photoRepository;
		this.characterRepository = characterRepository;
		this.recognitionService = recognitionService;
		this.evolutionService = evolutionService;
	}

	@Transactional
	public RecognitionResponse recognizePhoto(Long userId, Long photoId) {
		Photo photo = photoRepository.findById(photoId)
				.orElseThrow(() -> new ResourceNotFoundException("사진을 찾을 수 없습니다."));
		Character character = findCharacterByUserId(userId);

		if (!photo.getCharacter().getId().equals(character.getId())) {
			throw new IllegalArgumentException("다른 사용자의 사진은 인식할 수 없습니다.");
		}

		return recognitionRepository.findByPhotoId(photoId)
				.map(this::toResponse)
				.orElseGet(() -> createRecognition(photo));
	}

	@Transactional(readOnly = true)
	public List<RecognitionResponse> getMyRecognitions(Long userId) {
		Character character = findCharacterByUserId(userId);
		return recognitionRepository.findByPhotoCharacterId(character.getId()).stream()
				.map(this::toResponse)
				.toList();
	}

	private RecognitionResponse createRecognition(Photo photo) {
		RecognitionResult result = recognitionService.recognize(photo);
		Recognition recognition = recognitionRepository.save(Recognition.create(
				photo,
				result.recognizedObject(),
				result.confidence(),
				result.recognized()));
		if (recognition.isRecognized() && recognition.getRecognizedObject() != null) {
			evolutionService.addEvolutionPoint(
					photo.getCharacter().getId(), EvolutionSourceType.RECOGNITION);
		}

		// TODO: 다중 객체 인식, 공명 계산 및 Reward 시스템과 연결한다.
		return toResponse(recognition);
	}

	private Character findCharacterByUserId(Long userId) {
		return characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
	}

	private RecognitionResponse toResponse(Recognition recognition) {
		return new RecognitionResponse(
				recognition.getId(),
				recognition.getPhoto().getId(),
				recognition.getRecognizedObject(),
				recognition.getConfidence(),
				recognition.isRecognized());
	}
}
