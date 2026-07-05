package com.projecteden.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.projecteden.user.domain.User;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

	private final SecretKey secretKey;
	private final long accessTokenExpiration;

	public JwtTokenProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-token-expiration}") long accessTokenExpiration) {
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenExpiration = accessTokenExpiration;
	}

	public String generateAccessToken(User user) {
		Date issuedAt = new Date();
		Date expiration = new Date(issuedAt.getTime() + accessTokenExpiration);

		return Jwts.builder()
				.subject(String.valueOf(user.getId()))
				.claim("email", user.getEmail())
				.claim("role", user.getRole())
				.issuedAt(issuedAt)
				.expiration(expiration)
				.signWith(secretKey)
				.compact();
	}

	public Long getUserId(String token) {
		String subject = Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload()
				.getSubject();
		return Long.valueOf(subject);
	}

	public boolean validateToken(String token) {
		try {
			String subject = Jwts.parser()
					.verifyWith(secretKey)
					.build()
					.parseSignedClaims(token)
					.getPayload()
					.getSubject();
			Long.valueOf(subject);
			return true;
		} catch (JwtException | IllegalArgumentException exception) {
			return false;
		}
	}
}
