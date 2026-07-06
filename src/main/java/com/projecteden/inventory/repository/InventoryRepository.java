package com.projecteden.inventory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.inventory.domain.Inventory;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

	Optional<Inventory> findByHouseId(Long houseId);

	boolean existsByHouseId(Long houseId);
}
