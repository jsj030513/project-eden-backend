package com.projecteden.world.ecology;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.ai.dto.RecognitionResponse;
import com.projecteden.ai.repository.RecognitionRepository;
import com.projecteden.ai.service.RecognitionApplicationService;
import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.DuplicateResourceException;
import com.projecteden.common.exception.ForbiddenOperationException;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;

@Service
public class WorldPlantingService {

    private final CharacterRepository characters;
    private final PhotoRepository photos;
    private final RecognitionRepository recognitions;
    private final WorldPlacedObjectRepository objects;
    private final WorldChangeRepository changes;
    private final RecognitionApplicationService recognitionService;
    private final WorldEcologyService ecology;

    public WorldPlantingService(
            CharacterRepository characters,
            PhotoRepository photos,
            RecognitionRepository recognitions,
            WorldPlacedObjectRepository objects,
            WorldChangeRepository changes,
            RecognitionApplicationService recognitionService,
            WorldEcologyService ecology) {
        this.characters = characters;
        this.photos = photos;
        this.recognitions = recognitions;
        this.objects = objects;
        this.changes = changes;
        this.recognitionService = recognitionService;
        this.ecology = ecology;
    }

    @Transactional
    public PlantMemoryResponse plant(Long userId, PlantMemoryRequest request) {
        Character character = characters.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("캐릭터를 찾을 수 없습니다."));
        Photo photo = photos.findByIdForUpdate(request.photoId())
                .orElseThrow(() -> new ResourceNotFoundException("사진을 찾을 수 없습니다."));
        validatePhotoOwner(photo, character);

        WorldPlacedObject target = objects.findByIdForUpdate(request.targetId())
                .orElseThrow(() -> new ResourceNotFoundException("심기 대상을 찾을 수 없습니다."));
        validateTarget(character, target, request);

        var existingTargetChange = changes.findByTargetObjectId(target.getId());
        if (existingTargetChange.isPresent()) {
            WorldChange change = existingTargetChange.get();
            if (change.getRecognition() != null
                    && change.getRecognition().getPhoto().getId().equals(photo.getId())) {
                return response(photo, target, change.getRecognition(), change.getAssetType(), ecology.result(change));
            }
            throw conflict("TARGET_ALREADY_PLANTED");
        }

        Recognition existingRecognition = recognitions.findByPhotoId(photo.getId()).orElse(null);
        if (existingRecognition != null) {
            WorldPlacedObject attemptedTarget = existingRecognition.getPlantingTargetObject();
            if (attemptedTarget == null) {
                throw conflict("PHOTO_ALREADY_EXPRESSED");
            }
            if (!attemptedTarget.getId().equals(target.getId())) {
                throw conflict("PHOTO_ALREADY_EXPRESSED");
            }
            return nonPlantableResponse(photo, target, existingRecognition);
        }

        Recognition recognition = recognitionService.recognizeForPlanting(photo, target);
        WorldAssetType cropAsset = cropAssetFor(recognition);
        if (cropAsset == null) {
            return nonPlantableResponse(photo, target, recognition);
        }

        try {
            WorldChangeResult worldChange = ecology.createTargeted(recognition, target, cropAsset);
            return response(photo, target, recognition, cropAsset, worldChange);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("TARGET_ALREADY_PLANTED");
        }
    }

    private void validatePhotoOwner(Photo photo, Character character) {
        if (!photo.getCharacter().getId().equals(character.getId())) {
            throw new ForbiddenOperationException("다른 사용자의 사진은 심을 수 없습니다.");
        }
    }

    private void validateTarget(Character character, WorldPlacedObject target, PlantMemoryRequest request) {
        if (!target.getWorldChange().getCharacter().getId().equals(character.getId())) {
            throw new ForbiddenOperationException("다른 사용자의 심기 대상에는 접근할 수 없습니다.");
        }
        if (target.getAssetType() != WorldAssetType.FARM_PLOT_EMPTY) {
            throw conflict("TARGET_CHANGED");
        }
        int targetX = WorldCoordinates.pixelToTile(target.getPositionX());
        int targetY = WorldCoordinates.pixelToTile(target.getPositionY());
        if (targetX != request.expectedX() || targetY != request.expectedY()) {
            throw conflict("TARGET_CHANGED");
        }
        WorldPlayerPosition player = ecology.position(character);
        if (Math.abs(player.getX() - targetX) + Math.abs(player.getY() - targetY) != 1) {
            throw conflict("TARGET_OUT_OF_RANGE");
        }
    }

    private PlantMemoryResponse nonPlantableResponse(
            Photo photo,
            WorldPlacedObject target,
            Recognition recognition) {
        return response(photo, target, recognition, null, null);
    }

    private PlantMemoryResponse response(
            Photo photo,
            WorldPlacedObject target,
            Recognition recognition,
            WorldAssetType cropAsset,
            WorldChangeResult worldChange) {
        RecognitionResponse recognitionResponse = new RecognitionResponse(
                recognition.getId(),
                photo.getId(),
                recognition.getRecognizedObject(),
                recognition.getRecognizedObject().getCategory(),
                recognition.getConfidence(),
                recognition.isRecognized(),
                !recognition.isRecognized(),
                worldChange);
        return new PlantMemoryResponse(
                photo.getId(), target.getId(),
                WorldCoordinates.pixelToTile(target.getPositionX()),
                WorldCoordinates.pixelToTile(target.getPositionY()),
                cropAsset != null, cropAsset, recognitionResponse, worldChange);
    }

    static WorldAssetType cropAssetFor(Recognition recognition) {
        RecognizedObject object = recognition.getRecognizedObject();
        if (object == null) {
            return null;
        }
        return switch (object) {
            case FLOWER -> WorldAssetType.FARM_FLOWER;
            case CARROT -> WorldAssetType.FARM_CARROT;
            case TOMATO -> WorldAssetType.FARM_TOMATO;
            case VEGETABLE, PLANT -> WorldAssetType.FARM_VEGETABLE;
            default -> null;
        };
    }

    private static DuplicateResourceException conflict(String code) {
        return new DuplicateResourceException(code);
    }
}
