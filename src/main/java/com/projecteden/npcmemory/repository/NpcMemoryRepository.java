package com.projecteden.npcmemory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.npcmemory.domain.NpcMemory;

public interface NpcMemoryRepository extends JpaRepository<NpcMemory, Long> {

	Optional<NpcMemory> findByCharacterIdAndNpcId(Long characterId, Long npcId);
}
