package com.projecteden.photo.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.dto.PhotoResponse;
import com.projecteden.photo.dto.PhotoUploadResponse;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.plant.domain.Plant;
import com.projecteden.plant.domain.PlantStage;
import com.projecteden.plant.repository.PlantRepository;

@Service
public class PhotoService {

	private final PhotoRepository photoRepository;
	private final CharacterRepository characterRepository;
	private final PlantRepository plantRepository;

	public PhotoService(
			PhotoRepository photoRepository,
			CharacterRepository characterRepository,
			PlantRepository plantRepository) {
		this.photoRepository = photoRepository;
		this.characterRepository = characterRepository;
		this.plantRepository = plantRepository;
	}

	@Transactional
	public PhotoUploadResponse uploadPhoto(Long userId, Long plantId, MultipartFile file) {
		Character character = findCharacterByUserId(userId);
		Plant plant = plantRepository.findById(plantId)
				.orElseThrow(() -> new IllegalArgumentException("식물을 찾을 수 없습니다."));

		if (!plant.getCharacter().getId().equals(character.getId())) {
			throw new IllegalArgumentException("다른 사용자의 식물에는 사진을 업로드할 수 없습니다.");
		}
		if (plant.getPlantStage() != PlantStage.BLOOMED) {
			throw new IllegalArgumentException("개화한 식물에만 사진을 업로드할 수 있습니다.");
		}
		if (file.isEmpty()) {
			throw new IllegalArgumentException("업로드할 사진 파일이 필요합니다.");
		}
		if (photoRepository.findByPlantId(plantId).isPresent()) {
			throw new IllegalArgumentException("이미 사진이 등록된 식물입니다.");
		}

		String originalFileName = StringUtils.cleanPath(
				file.getOriginalFilename() == null ? "photo" : file.getOriginalFilename());
		String extension = StringUtils.getFilenameExtension(originalFileName);
		String storedFileName = UUID.randomUUID() + (extension == null ? "" : "." + extension);
		String imageUrl = "/uploads/photos/" + storedFileName;

		Photo photo = photoRepository.save(Photo.create(
				character,
				plant,
				originalFileName,
				storedFileName,
				file.getContentType(),
				file.getSize(),
				imageUrl));

		// TODO: AI Recognition -> Resonance -> Reward 시스템과 연결한다.
		// TODO: 향후 Plant 사진 여러 장을 지원하도록 연관관계를 확장한다.
		return new PhotoUploadResponse(photo.getId(), plant.getId(), photo.getImageUrl(), photo.getUploadedAt());
	}

	@Transactional(readOnly = true)
	public List<PhotoResponse> getMyPhotos(Long userId) {
		Character character = findCharacterByUserId(userId);
		return photoRepository.findByCharacterId(character.getId()).stream()
				.map(photo -> new PhotoResponse(photo.getId(), photo.getImageUrl(), photo.getUploadedAt()))
				.toList();
	}

	private Character findCharacterByUserId(Long userId) {
		return characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
	}
}
