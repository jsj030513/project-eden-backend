package com.projecteden.world.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.Valid;

import com.projecteden.user.domain.User;
import com.projecteden.world.dto.CreateWorldResponse;
import com.projecteden.world.service.WorldService;
import com.projecteden.world.ecology.WorldEcologyService;
import com.projecteden.world.ecology.WorldStateResponse;
import com.projecteden.world.ecology.MoveRequest;
import com.projecteden.world.ecology.MoveResponse;
import com.projecteden.world.ecology.PlantMemoryRequest;
import com.projecteden.world.ecology.PlantMemoryResponse;
import com.projecteden.world.ecology.WorldPlantingService;
import com.projecteden.world.chunk.WorldChunkQueryService;
import com.projecteden.world.chunk.WorldChunksResponse;
import com.projecteden.world.npc.NpcProgressNotification;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/worlds")
public class WorldController {

	private final WorldService worldService;
	private final WorldEcologyService worldEcologyService;
	private final WorldPlantingService worldPlantingService;
	private final WorldChunkQueryService worldChunkQueryService;

	public WorldController(WorldService worldService, WorldEcologyService worldEcologyService, WorldPlantingService worldPlantingService,
			WorldChunkQueryService worldChunkQueryService) {
		this.worldService = worldService;
		this.worldEcologyService = worldEcologyService;
		this.worldPlantingService = worldPlantingService;
		this.worldChunkQueryService = worldChunkQueryService;
	}

	@PostMapping
	public ResponseEntity<CreateWorldResponse> createWorld(@AuthenticationPrincipal User user) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(worldService.createMyWorld(user.getId()));
	}

	@GetMapping("/me")
	public ResponseEntity<CreateWorldResponse> getMyWorld(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(worldService.getMyWorld(user.getId()));
	}

	@GetMapping("/me/state")
	public ResponseEntity<WorldStateResponse> getMyWorldState(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(worldEcologyService.stateForUser(user.getId()));
	}

	@GetMapping("/me/chunks")
	public ResponseEntity<WorldChunksResponse> getMyWorldChunks(
			@AuthenticationPrincipal User user,
			@RequestParam int centerChunkX,
			@RequestParam int centerChunkY,
			@RequestParam(defaultValue = "1") int radius) {
		return ResponseEntity.ok(worldChunkQueryService.chunksForUser(
				user.getId(), centerChunkX, centerChunkY, radius));
	}
	@PostMapping("/me/move") public ResponseEntity<MoveResponse> move(@AuthenticationPrincipal User user,@RequestBody MoveRequest request){return ResponseEntity.ok(worldEcologyService.move(user.getId(),request));}

	@PostMapping("/me/interactions/{targetId}/progress")
	public ResponseEntity<List<NpcProgressNotification>> recordInteraction(
			@AuthenticationPrincipal User user,
			@PathVariable Long targetId) {
		return ResponseEntity.ok(worldEcologyService.recordInteraction(user.getId(), targetId));
	}

	@PostMapping("/me/plant-memory")
	public ResponseEntity<PlantMemoryResponse> plantMemory(
			@AuthenticationPrincipal User user,
			@Valid @RequestBody PlantMemoryRequest request) {
		return ResponseEntity.ok(worldPlantingService.plant(user.getId(), request));
	}
}
