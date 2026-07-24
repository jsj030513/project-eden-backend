package com.projecteden.auth.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.auth.dto.LoginRequest;
import com.projecteden.auth.dto.LoginResponse;
import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.common.exception.AuthenticationFailureException;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.user.service.EmailNormalizer;

@Service
public class AuthService {

	private static final String LOGIN_FAILURE_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;

	public AuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Transactional
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(EmailNormalizer.normalize(request.email()))
				.orElseThrow(() -> new AuthenticationFailureException(LOGIN_FAILURE_MESSAGE));

		if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new AuthenticationFailureException(LOGIN_FAILURE_MESSAGE);
		}
		user.recordLogin(LocalDateTime.now());

		String accessToken = jwtTokenProvider.generateAccessToken(user);
		return new LoginResponse(
				accessToken,
				"Bearer",
				user.getId(),
				user.getEmail(),
				user.getNickname());
	}
}
