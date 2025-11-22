package org.example.tokenbrutal.controller;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

import de.huxhorn.sulky.ulid.ULID;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.tokenbrutal.controller.request.LoginRequest;
import org.example.tokenbrutal.controller.response.LoginResponse;
import org.example.tokenbrutal.controller.response.RefreshTokenResponse;
import org.example.tokenbrutal.entity.TokenEntity;
import org.example.tokenbrutal.persistent.UserSession;
import org.example.tokenbrutal.repository.UserSessionRepository;
import org.example.tokenbrutal.util.TokenUtil;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
	public static final String REFRESH_TOKEN_PREFIX = "refreshToken:";

	private final UserSessionRepository userSessionRepository;
	private final RedisOperations<String, Object> redisOperations;
	public final ULID ulid;

	@PostMapping("/login")
	@Transactional
	public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
		if ("admin".equals(request.username()) && "12345".equals(request.password())) {
			String refreshToken = TokenUtil.generateOpaqueToken();
			ResponseCookie cookie = getResponseCookie(refreshToken);
			response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
			UserSession userSession = saveSession(request, refreshToken);
			String accessToken = TokenUtil.generateJwtToken(request.username(), userSession.getId());
			cacheAccessToken(refreshToken, accessToken, userSession);
			LoginResponse tokenResponse = LoginResponse.builder()
					.accessToken(accessToken)
					.message("Login successful")
					.build();
			return ResponseEntity.ok(tokenResponse);
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
	}

	private void cacheAccessToken(String refreshToken, String accessToken, UserSession userSession){
		TokenEntity tokenEntity = TokenEntity.builder()
				.token(accessToken)
				.username(userSession.getUsername())
				.build();
		redisOperations.opsForValue().set(REFRESH_TOKEN_PREFIX + refreshToken, tokenEntity, Duration.ofSeconds(TokenUtil.ACCESS_TOKEN_EXPIRATION));
	}

	private UserSession saveSession(LoginRequest request, String refreshToken){
		UserSession userSession = new UserSession();
		userSession.setUsername(request.username());
		Instant creationTime = Instant.now();
		userSession.setExpirationTime(creationTime.plusSeconds(TokenUtil.REFRESH_TOKEN_MAX_AGE_SECONDS));
		userSession.setRefreshToken(refreshToken);
		userSession.setCreationTime(creationTime);
		userSession.setId(ulid.nextULID());
		return userSessionRepository.save(userSession);
	}

	private static ResponseCookie getResponseCookie(String refreshToken){
		return ResponseCookie.from("refresh_token", refreshToken)
				.httpOnly(true)
				.secure(false)
				.path("/auth/refresh")
				.maxAge(TokenUtil.REFRESH_TOKEN_MAX_AGE_SECONDS)
				.sameSite("Lax")
				.build();
	}

	@PostMapping("/refresh")
	public ResponseEntity<RefreshTokenResponse> refresh(@CookieValue(value = "refresh_token", required = false) String refreshToken) {
		if (refreshToken == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		TokenEntity cachedToken = (TokenEntity) redisOperations.opsForValue().get(REFRESH_TOKEN_PREFIX + refreshToken);
		if(cachedToken != null){
			RefreshTokenResponse tokenResponse = RefreshTokenResponse.builder()
					.accessToken(cachedToken.token())
					.username(cachedToken.username())
					.message("Token refreshed")
					.build();
			return ResponseEntity.ok(tokenResponse);
		}
		UserSession userSession = userSessionRepository.findByRefreshToken(refreshToken).orElse(null);
		if (userSession == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		if(userSession.getExpirationTime().isBefore(Instant.now())){
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		String username = userSession.getUsername();
		String newAccessToken = TokenUtil.generateJwtToken(username, userSession.getId());
		cacheAccessToken(refreshToken, newAccessToken, userSession);
		RefreshTokenResponse tokenResponse = RefreshTokenResponse.builder()
				.accessToken(newAccessToken)
				.username(username)
				.message("Token refreshed")
				.build();
		return ResponseEntity.ok(tokenResponse);
	}

	@GetMapping("/logout")
	@Transactional
	public ResponseEntity<?> logout(Authentication authentication, HttpServletResponse response) {
		Claims principal = (Claims) authentication.getPrincipal();
		String sessionId = principal.get("sessionId", String.class);
		if(sessionId != null){
			Instant now = Instant.now();
			userSessionRepository.findById(sessionId)
					.filter(userSession -> userSession.getExpirationTime().isAfter(now))
					.ifPresent(user -> {
						user.setExpirationTime(now);
						userSessionRepository.save(user);
						redisOperations.delete(REFRESH_TOKEN_PREFIX + user.getRefreshToken());
					});
		}
		Cookie cookie = new Cookie("refresh_token", "");
		cookie.setPath("/auth/refresh");
		cookie.setHttpOnly(true);
		cookie.setMaxAge(0);
		response.addCookie(cookie);
		return ResponseEntity.ok(LoginResponse.builder().accessToken("").message("Logged out").build());
	}

}
