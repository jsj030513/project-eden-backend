package com.projecteden.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.projecteden.user.domain.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

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
				.claim("role", user.getRole())
				.issuedAt(issuedAt)
				.expiration(expiration)
				.signWith(secretKey)
				.compact();
	}

	public Long getUserId(String token) {
		String subject = parseClaims(token).getSubject();
		return Long.valueOf(subject);
	}

	public boolean validateToken(String token) {
		try {
			String subject = parseClaims(token).getSubject();
			Long.valueOf(subject);
			return true;
		} catch (JwtException | IllegalArgumentException exception) {
			return false;
		}
	}

	public JwtDiagnostics diagnose(String token) {
		try {
			Claims claims = parseClaims(token);
			String subject = claims.getSubject();
			if (subject == null || subject.isBlank()) {
				return JwtDiagnostics.invalid("EMPTY_SUBJECT", null, false);
			}
			Long.valueOf(subject);
			return new JwtDiagnostics(true, false, subject, null);
		} catch (ExpiredJwtException exception) {
			return JwtDiagnostics.invalid("EXPIRED", exception.getClaims().getSubject(), true);
		} catch (MalformedJwtException exception) {
			return JwtDiagnostics.invalid("MALFORMED", null, false);
		} catch (SignatureException exception) {
			return JwtDiagnostics.invalid("INVALID_SIGNATURE", null, false);
		} catch (UnsupportedJwtException exception) {
			return JwtDiagnostics.invalid("UNSUPPORTED", null, false);
		} catch (IllegalArgumentException exception) {
			return JwtDiagnostics.invalid("EMPTY_OR_INVALID_SUBJECT", null, false);
		} catch (JwtException exception) {
			return JwtDiagnostics.invalid(exception.getClass().getSimpleName(), null, false);
		}
	}

	private Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	public record JwtDiagnostics(
			boolean valid,
			boolean expired,
			String subject,
			String failureType
	) {

		private static JwtDiagnostics invalid(String failureType, String subject, boolean expired) {
			return new JwtDiagnostics(false, expired, subject, failureType);
		}
	}
}
