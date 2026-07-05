package com.projecteden.character.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.character.domain.Character;

public interface CharacterRepository extends JpaRepository<Character, Long> {

	Optional<Character> findByUserId(Long userId);

	boolean existsByUserId(Long userId);
}
