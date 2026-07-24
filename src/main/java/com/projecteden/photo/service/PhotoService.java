package com.projecteden.photo.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.dto.PhotoResponse;
import com.projecteden.photo.dto.PhotoUploadResponse;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.photo.storage.PhotoStorageService;
import com.projecteden.photo.storage.PhotoUploadValidator;
import com.projecteden.photo.storage.ValidatedPhotoUpload;
import com.projecteden.plant.domain.Plant;
import com.projecteden.plant.domain.PlantStage;
import com.projecteden.plant.repository.PlantRepository;

@Service
public class PhotoService {

	private final PhotoRepository photoRepository;
	private final CharacterRepository characterRepository;
	private final PlantRepository plantRepository;
	private final PhotoStorageService photoStorageService;
	private final PhotoUploadValidator photoUploadValidator;

	public PhotoService(
			PhotoRepository photoRepository,
			CharacterRepository characterRepository,
			PlantRepository plantRepository,
			PhotoStorageService photoStorageService,
			PhotoUploadValidator photoUploadValidator) {
		this.photoRepository = photoRepository;
		this.characterRepository = characterRepository;
		this.plantRepository = plantRepository;
		this.photoStorageService = photoStorageService;
		this.photoUploadValidator = photoUploadValidator;
	}

	@Transactional
	public PhotoUploadResponse uploadPhoto(Long userId, Long plantId, MultipartFile file) {
		Character character = findCharacterByUserId(userId);
		Plant plant = findUploadPlant(character, plantId);
		ValidatedPhotoUpload upload = photoUploadValidator.validate(file);

		String storedFileName = UUID.randomUUID() + "." + upload.extension();
		String imageUrl = "/uploads/photos/" + storedFileName;
		photoStorageService.store(storedFileName, upload.bytes());

		try {
			Photo photo = photoRepository.save(Photo.create(
					character,
					plant,
					upload.originalFileName(),
					storedFileName,
					upload.contentType(),
					upload.size(),
					imageUrl));

			// TODO: AI Recognition -> Resonance -> Reward 시스템과 연결한다.
			// TODO: 향후 Plant 사진 여러 장을 지원하도록 연관관계를 확장한다.
			return new PhotoUploadResponse(
					photo.getId(), plant == null ? null : plant.getId(), photo.getImageUrl(), photo.getUploadedAt());
		} catch (RuntimeException exception) {
			try {
				photoStorageService.delete(storedFileName);
			} catch (RuntimeException ignored) {
				// The persistence failure remains the actionable failure.
			}
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public List<PhotoResponse> getMyPhotos(Long userId) {
		Character character = findCharacterByUserId(userId);
		return photoRepository.findByCharacterId(character.getId()).stream()
				.map(photo -> new PhotoResponse(
						photo.getId(),
						photo.getPlant() == null ? null : photo.getPlant().getId(),
						photo.getImageUrl(),
						photo.getUploadedAt()))
				.toList();
	}

	private Plant findUploadPlant(Character character, Long plantId) {
		if (plantId == null) {
			return null;
		}

		Plant plant = plantRepository.findById(plantId)
				.orElseThrow(() -> new ResourceNotFoundException("식물을 찾을 수 없습니다."));

		if (!plant.getCharacter().getId().equals(character.getId())) {
			throw new IllegalArgumentException("다른 사용자의 식물에는 사진을 업로드할 수 없습니다.");
		}
		if (plant.getPlantStage() != PlantStage.BLOOMED) {
			throw new IllegalArgumentException("개화한 식물에만 사진을 업로드할 수 있습니다.");
		}
		if (photoRepository.findByPlantId(plantId).isPresent()) {
			throw new IllegalArgumentException("이미 사진이 등록된 식물입니다.");
		}
		return plant;
	}

	private Character findCharacterByUserId(Long userId) {
		return characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
	}
}
