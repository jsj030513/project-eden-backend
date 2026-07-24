package com.projecteden.auth.jwt;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.projecteden.user.repository.UserRepository;
import com.projecteden.user.domain.User;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;
	private final UserRepository userRepository;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");

		if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
			String token = authorization.substring(BEARER_PREFIX.length());
			JwtTokenProvider.JwtDiagnostics diagnostics = jwtTokenProvider.diagnose(token);

			if (diagnostics.valid()) {
				Long userId = Long.valueOf(diagnostics.subject());
				userRepository.findById(userId).filter(User::isActive).ifPresent(user -> {
					SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole());
					UsernamePasswordAuthenticationToken authentication =
							new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
					SecurityContextHolder.getContext().setAuthentication(authentication);
				});
			}
		}

		filterChain.doFilter(request, response);
	}
}
