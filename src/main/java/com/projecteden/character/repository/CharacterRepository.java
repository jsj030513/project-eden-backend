package com.projecteden.character.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.projecteden.character.domain.Character;

public interface CharacterRepository extends JpaRepository<Character, Long> {

	Optional<Character> findByUserId(Long userId);

	@EntityGraph(attributePaths = "user")
	List<Character> findByUserIdIn(Collection<Long> userIds);

	boolean existsByUserId(Long userId);
}
