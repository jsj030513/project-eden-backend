package com.projecteden.resonance.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.ai.repository.RecognitionRepository;
import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.house.domain.House;
import com.projecteden.house.repository.HouseRepository;
import com.projecteden.inventory.domain.Inventory;
import com.projecteden.inventory.repository.InventoryRepository;
import com.projecteden.resonance.domain.Resonance;
import com.projecteden.resonance.domain.ResonanceRewardType;
import com.projecteden.resonance.dto.ResonanceResponse;
import com.projecteden.resonance.repository.ResonanceRepository;
import com.projecteden.seed.domain.Seed;
import com.projecteden.seed.domain.SeedType;
import com.projecteden.seed.repository.SeedRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@Service
public class ResonanceService {

	private static final int DAILY_RESONANCE_LIMIT = 10;

	private final ResonanceRepository resonanceRepository;
	private final RecognitionRepository recognitionRepository;
	private final CharacterRepository characterRepository;
	private final WorldRepository worldRepository;
	private final HouseRepository houseRepository;
	private final InventoryRepository inventoryRepository;
	private final SeedRepository seedRepository;
	private final Clock clock;

	public ResonanceService(
			ResonanceRepository resonanceRepository,
			RecognitionRepository recognitionRepository,
			CharacterRepository characterRepository,
			WorldRepository worldRepository,
			HouseRepository houseRepository,
			InventoryRepository inventoryRepository,
			SeedRepository seedRepository,
			Clock clock) {
		this.resonanceRepository = resonanceRepository;
		this.recognitionRepository = recognitionRepository;
		this.characterRepository = characterRepository;
		this.worldRepository = worldRepository;
		this.houseRepository = houseRepository;
		this.inventoryRepository = inventoryRepository;
		this.seedRepository = seedRepository;
		this.clock = clock;
	}

	@Transactional
	public ResonanceResponse createMyResonanceReward(Long userId, Long recognitionId) {
		Character character = findCharacterByUserId(userId);
		return createResonanceReward(character.getId(), recognitionId);
	}

	@Transactional
	public ResonanceResponse createResonanceReward(Long characterId, Long recognitionId) {
		Character character = characterRepository.findById(characterId)
				.orElseThrow(() -> new ResourceNotFoundException("캐릭터를 찾을 수 없습니다."));
		Recognition recognition = recognitionRepository.findById(recognitionId)
				.orElseThrow(() -> new ResourceNotFoundException("인식 결과를 찾을 수 없습니다."));

		validateRecognition(character, recognition);
		LocalDate today = LocalDate.now(clock);
		validateDailyLimits(characterId, recognition, today);

		Reward reward = calculateReward(recognition.getRecognizedObject());
		grantReward(characterId, reward);

		Resonance resonance = resonanceRepository.save(Resonance.create(
				character,
				recognition,
				recognition.getRecognizedObject(),
				reward.rewardType(),
				reward.seedType(),
				reward.seedQuantity(),
				reward.gold(),
				today));
		return toResponse(resonance);
	}

	@Transactional(readOnly = true)
	public List<ResonanceResponse> getMyResonances(Long userId) {
		Character character = findCharacterByUserId(userId);
		return resonanceRepository.findByCharacterId(character.getId()).stream()
				.map(this::toResponse)
				.toList();
	}

	private void validateRecognition(Character character, Recognition recognition) {
		if (!recognition.getPhoto().getCharacter().getId().equals(character.getId())) {
			throw new IllegalArgumentException("다른 사용자의 인식 결과로 공명할 수 없습니다.");
		}
		if (!recognition.isRecognized()) {
			throw new IllegalArgumentException("인식에 실패한 결과로는 공명할 수 없습니다.");
		}
		if (resonanceRepository.existsByRecognitionId(recognition.getId())) {
			throw new IllegalArgumentException("이미 공명이 발생한 인식 결과입니다.");
		}
	}

	private void validateDailyLimits(Long characterId, Recognition recognition, LocalDate today) {
		if (resonanceRepository.existsByCharacterIdAndRecognizedObjectAndResonanceDate(
				characterId, recognition.getRecognizedObject(), today)) {
			throw new IllegalArgumentException("오늘 이미 같은 대상의 공명 보상을 받았습니다.");
		}
		if (resonanceRepository.countByCharacterIdAndResonanceDate(characterId, today)
				>= DAILY_RESONANCE_LIMIT) {
			throw new IllegalArgumentException("오늘의 공명 보상 횟수를 모두 사용했습니다.");
		}
	}

	private Reward calculateReward(RecognizedObject recognizedObject) {
		if (recognizedObject == RecognizedObject.UNKNOWN) {
			return new Reward(ResonanceRewardType.NONE, null, 0, 5);
		}
		return new Reward(
				ResonanceRewardType.SEED,
				SeedType.valueOf(recognizedObject.name()),
				1,
				0);
	}

	private void grantReward(Long characterId, Reward reward) {
		World world = worldRepository.findByCharacterId(characterId)
				.orElseThrow(() -> new IllegalArgumentException("월드를 찾을 수 없습니다."));
		if (reward.gold() > 0) {
			world.addGold(reward.gold());
		}
		if (reward.seedType() == null) {
			return;
		}

		House house = houseRepository.findByWorldId(world.getId())
				.orElseThrow(() -> new IllegalArgumentException("집을 찾을 수 없습니다."));
		Inventory inventory = inventoryRepository.findByHouseId(house.getId())
				.orElseThrow(() -> new IllegalArgumentException("인벤토리를 찾을 수 없습니다."));
		Seed seed = seedRepository.findByInventoryIdAndSeedType(inventory.getId(), reward.seedType())
				.orElseGet(() -> Seed.create(inventory, reward.seedType(), 0));
		seed.addQuantity(reward.seedQuantity());
		seedRepository.save(seed);
	}

	private Character findCharacterByUserId(Long userId) {
		return characterRepository.findByUserId(userId)
				.orElseThrow(() -> new ResourceNotFoundException("캐릭터를 찾을 수 없습니다."));
	}

	private ResonanceResponse toResponse(Resonance resonance) {
		return new ResonanceResponse(
				resonance.getId(),
				resonance.getRecognizedObject(),
				resonance.getRewardType(),
				resonance.getRewardSeedType(),
				resonance.getRewardSeedQuantity(),
				resonance.getRewardGold(),
				resonance.getResonanceDate(),
				messageFor(resonance));
	}

	private String messageFor(Resonance resonance) {
		if (resonance.getRewardSeedType() != null) {
			return resonance.getRecognizedObject() + "와 공명하여 "
					+ resonance.getRewardSeedType() + " 씨앗을 획득했습니다.";
		}
		return "UNKNOWN과 약하게 공명하여 골드를 획득했습니다.";
	}

	private record Reward(
			ResonanceRewardType rewardType,
			SeedType seedType,
			int seedQuantity,
			int gold) {
	}
}
