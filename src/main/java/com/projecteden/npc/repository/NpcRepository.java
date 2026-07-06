package com.projecteden.npc.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.npc.domain.Npc;

public interface NpcRepository extends JpaRepository<Npc, Long> {

	List<Npc> findByRegionId(Long regionId);

	List<Npc> findByRegionWorldId(Long worldId);
}
