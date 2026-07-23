package com.projecteden.ai.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.dto.RecognitionResponse;
import com.projecteden.ai.dto.RecognitionResult;
import com.projecteden.ai.repository.RecognitionRepository;
import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.evolution.domain.EvolutionSourceType;
import com.projecteden.evolution.service.EvolutionService;
import com.projecteden.imagenormalization.ImageNormalizationException;
import com.projecteden.imagenormalization.ImageNormalizationService;
import com.projecteden.imagenormalization.NormalizedImage;
import com.projecteden.memorytaxonomy.classification.MemoryClassificationService;
import com.projecteden.memorytaxonomy.legacy.LegacyRecognitionCompletedEvent;
import com.projecteden.memorytaxonomy.observation.ImageObservation;
import com.projecteden.memorytaxonomy.observation.ImageObservationProviderResolver;
import com.projecteden.memorytaxonomy.observation.ImageObservationRequest;
import com.projecteden.memorytaxonomy.observation.LegacyRecognitionProjection;
import com.projecteden.memorytaxonomy.observation.MockImageObservationProvider;
import com.projecteden.memorytaxonomy.observation.UploadedImagePayload;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.photo.storage.PhotoStorageService;
import com.projecteden.village.service.VillageService;
import com.projecteden.world.ecology.WorldEcologyService;
import com.projecteden.world.ecology.WorldPlacedObject;

@Service
public class RecognitionApplicationService {

	private static final Logger log = LoggerFactory.getLogger(RecognitionApplicationService.class);

	private final RecognitionRepository recognitionRepository;
	private final PhotoRepository photoRepository;
	private final CharacterRepository characterRepository;
	private final ImageObservationProviderResolver imageObservationProviderResolver;
	private final LegacyRecognitionProjection legacyRecognitionProjection;
	private final MemoryClassificationService memoryClassificationService;
	private final ImageNormalizationService imageNormalizationService;
	private final MockImageObservationProvider mockImageObservationProvider;
	private final EvolutionService evolutionService;
	private final VillageService villageService;
	private final ApplicationEventPublisher eventPublisher;
	private final PhotoStorageService photoStorageService;
	private final WorldEcologyService worldEcologyService;

	public RecognitionApplicationService(
			RecognitionRepository recognitionRepository,
			PhotoRepository photoRepository,
			CharacterRepository characterRepository,
			ImageObservationProviderResolver imageObservationProviderResolver,
			LegacyRecognitionProjection legacyRecognitionProjection,
			MemoryClassificationService memoryClassificationService,
			ImageNormalizationService imageNormalizationService,
			MockImageObservationProvider mockImageObservationProvider,
			EvolutionService evolutionService,
			VillageService villageService,
			ApplicationEventPublisher eventPublisher,
			PhotoStorageService photoStorageService,
			WorldEcologyService worldEcologyService) {
		this.recognitionRepository = recognitionRepository;
		this.photoRepository = photoRepository;
		this.characterRepository = characterRepository;
		this.imageObservationProviderResolver = imageObservationProviderResolver;
		this.legacyRecognitionProjection = legacyRecognitionProjection;
		this.memoryClassificationService = memoryClassificationService;
		this.imageNormalizationService = imageNormalizationService;
		this.mockImageObservationProvider = mockImageObservationProvider;
		this.evolutionService = evolutionService;
		this.villageService = villageService;
		this.eventPublisher = eventPublisher;
		this.photoStorageService = photoStorageService;
		this.worldEcologyService = worldEcologyService;
	}

	@Transactional
	public RecognitionResponse recognizePhoto(Long userId, Long photoId) {
		Photo photo = photoRepository.findById(photoId)
				.orElseThrow(() -> new ResourceNotFoundException("사진을 찾을 수 없습니다."));
		Character character = findCharacterByUserId(userId);

		validatePhotoOwner(photo, character);

		return recognitionRepository.findByPhotoId(photoId)
				.map(this::toResponse)
				.orElseGet(() -> toResponse(createRecognition(photo, null, null, true)));
	}

	@Transactional
	public RecognitionResponse recognizePhotoWithImage(Long userId, Long photoId, MultipartFile file) {
		Photo photo = photoRepository.findById(photoId)
				.orElseThrow(() -> new ResourceNotFoundException("사진을 찾을 수 없습니다."));
		Character character = findCharacterByUserId(userId);
		validatePhotoOwner(photo, character);
		UploadedImagePayload payload = UploadedImagePayload.from(file);

		return recognitionRepository.findByPhotoId(photoId)
				.map(this::toResponse)
				.orElseGet(() -> toResponse(createRecognition(photo, payload, null, true)));
	}

	@Transactional
	public Recognition recognizeForPlanting(Photo photo, WorldPlacedObject targetObject) {
		return recognitionRepository.findByPhotoId(photo.getId())
				.orElseGet(() -> createRecognition(photo, null, targetObject, false));
	}

	@Transactional(readOnly = true)
	public List<RecognitionResponse> getMyRecognitions(Long userId) {
		Character character = findCharacterByUserId(userId);
		return recognitionRepository.findByPhotoCharacterId(character.getId()).stream()
				.map(this::toResponse)
				.toList();
	}

	private Recognition createRecognition(
			Photo photo,
			UploadedImagePayload payload,
			WorldPlacedObject plantingTargetObject,
			boolean createWorldExpression) {
		ImageObservation observation = observe(photo, payload);
		log.info("Photo recognition completed photoId={} provider={} modelVersion={} recognized={} fallback={} confidence={} objectCodes={}",
				photo.getId(), observation.provider(), observation.modelVersion(), observation.recognized(),
				observation.fallback(), observation.confidence(), observation.objects());
		memoryClassificationService.classify(observation);
		RecognitionResult result = legacyRecognitionProjection.project(observation);
		Recognition recognition = recognitionRepository.save(plantingTargetObject == null
				? Recognition.create(photo, result.recognizedObject(), result.confidence(), result.recognized())
				: Recognition.createForPlanting(
						photo, result.recognizedObject(), result.confidence(), result.recognized(), plantingTargetObject));
		eventPublisher.publishEvent(new LegacyRecognitionCompletedEvent(
				photo.getId(),
				recognition.getId()));
		if (recognition.isRecognized() && recognition.getRecognizedObject() != null) {
			evolutionService.addEvolutionPoint(
					photo.getCharacter().getId(), EvolutionSourceType.RECOGNITION);
		}
		if (recognition.getRecognizedObject() != null) {
			villageService.recordVillageMemory(
					photo.getCharacter().getId(), recognition.getRecognizedObject());
		}
		if (createWorldExpression) {
			worldEcologyService.createFor(recognition);
		}

		// TODO: 다중 객체 인식, 공명 계산 및 Reward 시스템과 연결한다.
		return recognition;
	}

	private ImageObservation observe(Photo photo, UploadedImagePayload payload) {
		if (payload == null) {
			payload = photoStorageService.load(photo).orElse(null);
		}
		if (payload == null) {
			return imageObservationProviderResolver.resolve()
					.observe(ImageObservationRequest.from(photo));
		}
		try {
			NormalizedImage normalized = imageNormalizationService.normalize(payload);
			return imageObservationProviderResolver.resolve()
					.observe(ImageObservationRequest.from(photo, normalized));
		} catch (ImageNormalizationException exception) {
			log.warn("Image normalization fallback photoId={} errorCode={}",
					photo.getId(), exception.getErrorCode());
			return mockImageObservationProvider.observe(ImageObservationRequest.from(photo, payload));
		}
	}

	private Character findCharacterByUserId(Long userId) {
		return characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
	}

	private void validatePhotoOwner(Photo photo, Character character) {
		if (!photo.getCharacter().getId().equals(character.getId())) {
			throw new IllegalArgumentException("다른 사용자의 사진은 인식할 수 없습니다.");
		}
	}

	private RecognitionResponse toResponse(Recognition recognition) {
		return new RecognitionResponse(
				recognition.getId(),
				recognition.getPhoto().getId(),
				recognition.getRecognizedObject(),
				recognition.getRecognizedObject().getCategory(),
				recognition.getConfidence(),
				recognition.isRecognized(),
				!recognition.isRecognized(),
				worldEcologyService.findFor(recognition.getId()));
	}
}
