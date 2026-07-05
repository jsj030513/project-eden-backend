package com.projecteden.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.user.domain.User;
import com.projecteden.user.dto.SignupRequest;
import com.projecteden.user.dto.SignupResponse;
import com.projecteden.user.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
		}

		User user = new User(
				request.email(),
				passwordEncoder.encode(request.password()),
				request.nickname());
		User savedUser = userRepository.save(user);

		return new SignupResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getNickname());
	}
}
