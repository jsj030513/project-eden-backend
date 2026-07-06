package com.projecteden.inventory.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.inventory.dto.CreateInventoryResponse;
import com.projecteden.inventory.service.InventoryService;
import com.projecteden.user.domain.User;

@RestController
@RequestMapping("/api/inventories")
public class InventoryController {

	private final InventoryService inventoryService;

	public InventoryController(InventoryService inventoryService) {
		this.inventoryService = inventoryService;
	}

	@PostMapping
	public ResponseEntity<CreateInventoryResponse> createInventory(@AuthenticationPrincipal User user) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(inventoryService.createMyInventory(user.getId()));
	}

	@GetMapping("/me")
	public ResponseEntity<CreateInventoryResponse> getMyInventory(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(inventoryService.getMyInventory(user.getId()));
	}
}
