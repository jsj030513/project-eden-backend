package com.projecteden.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.projecteden.user.domain.User;

import io.jsonwebtoken.Jwts;

class JwtTokenProviderTests {

	private static final String TEST_SECRET = "auth-hotfix-test-secret-must-be-long-enough-for-hmac-sha";

	@Test
	void acceptsValidAccessTokenWithMinimalClaims() {
		JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, 3_600_000);
		String token = provider.generateAccessToken(user());

		assertThat(provider.validateToken(token)).isTrue();
		assertThat(provider.getUserId(token)).isEqualTo(1L);
		assertThat(provider.diagnose(token).failureType()).isNull();

		String payload = new String(
				Base64.getUrlDecoder().decode(token.split("\\.")[1]),
				StandardCharsets.UTF_8);
		assertThat(payload).contains("\"sub\":\"1\"", "\"role\":\"USER\"");
		assertThat(payload).doesNotContain("email", "password");
	}

	@Test
	void rejectsTamperedAccessToken() {
		JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, 3_600_000);
		String token = provider.generateAccessToken(user());
		String tampered = token.substring(0, token.length() - 1)
				+ (token.endsWith("a") ? "b" : "a");

		assertThat(provider.validateToken(tampered)).isFalse();
		assertThat(provider.diagnose(tampered).valid()).isFalse();
	}

	@Test
	void rejectsExpiredAccessToken() {
		JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, -1);
		String token = provider.generateAccessToken(user());

		assertThat(provider.validateToken(token)).isFalse();
		assertThat(provider.diagnose(token).expired()).isTrue();
	}

	@Test
	void rejectsMalformedBlankAndUnsupportedTokens() {
		JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, 3_600_000);
		String unsupported = Jwts.builder().subject("1").compact();

		assertThat(provider.validateToken("not-a-jwt")).isFalse();
		assertThat(provider.diagnose("not-a-jwt").failureType()).isEqualTo("MALFORMED");
		assertThat(provider.validateToken("")).isFalse();
		assertThat(provider.diagnose("").failureType()).isEqualTo("EMPTY_OR_INVALID_SUBJECT");
		assertThat(provider.validateToken(unsupported)).isFalse();
		assertThat(provider.diagnose(unsupported).failureType()).isEqualTo("UNSUPPORTED");
	}

	@Test
	void rejectsTokenSignedWithAnotherSecret() {
		JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, 3_600_000);
		JwtTokenProvider anotherProvider = new JwtTokenProvider(
				"another-test-only-secret-key-that-is-long-enough-for-hmac",
				3_600_000);
		String token = anotherProvider.generateAccessToken(user());

		assertThat(provider.validateToken(token)).isFalse();
		assertThat(provider.diagnose(token).failureType()).isEqualTo("INVALID_SIGNATURE");
	}

	private User user() {
		User user = new User("jwt@example.com", "encoded-password", "jwt-user");
		ReflectionTestUtils.setField(user, "id", 1L);
		return user;
	}
}
