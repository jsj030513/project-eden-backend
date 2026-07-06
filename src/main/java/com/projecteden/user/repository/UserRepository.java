package com.projecteden.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	Optional<User> findByNickname(String nickname);

	boolean existsByEmail(String email);
}
