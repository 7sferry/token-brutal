package org.example.tokenbrutal.controller;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

import de.huxhorn.sulky.ulid.ULID;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.tokenbrutal.controller.request.LoginRequest;
import org.example.tokenbrutal.controller.response.TokenResponse;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Slf4j
public class AuthController{
	public static final String REFRESH_TOKEN_PREFIX = "refreshToken:";
	public static final String REFRESH_TOKEN = "refresh_token";

	private final UserSessionRepository userSessionRepository;
	private final RedisOperations<String, Object> redisOperations;
	private final ULID ulid;
	private final PlatformTransactionManager transactionManager;

	@PostMapping("/login")
	@Transactional
	public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request, HttpServletResponse response){
		if("admin".equals(request.username()) && "12345".equals(request.password())){
			String refreshToken = TokenUtil.generateOpaqueToken();
			String hashedRefreshToken = TokenUtil.hash(refreshToken);
			UserSession userSession = saveSession(request, hashedRefreshToken);
			String accessToken = TokenUtil.generateJwtToken(request.username(), userSession.getId());
			cacheAccessToken(hashedRefreshToken, accessToken, userSession);
			TokenResponse tokenResponse = TokenResponse.builder()
					.accessToken(accessToken)
					.message("Login successful")
					.build();
			ResponseCookie cookie = getResponseCookie(refreshToken);
			response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
			return ResponseEntity.ok(tokenResponse);
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
	}

	private void cacheAccessToken(String hashedRefreshToken, String accessToken, UserSession userSession){
		TokenEntity tokenEntity = TokenEntity.builder()
				.token(accessToken)
				.username(userSession.getUsername())
				.build();
		redisOperations.opsForValue().set(REFRESH_TOKEN_PREFIX + hashedRefreshToken, tokenEntity, Duration.ofMillis(TokenUtil.ACCESS_TOKEN_EXPIRATION_MS));
	}

	private UserSession saveSession(LoginRequest request, String hashedRefreshToken){
		UserSession userSession = new UserSession();
		userSession.setUsername(request.username());
		Instant creationTime = Instant.now();
		userSession.setExpirationTime(creationTime.plusSeconds(TokenUtil.REFRESH_TOKEN_MAX_AGE_SECONDS));
		userSession.setRefreshToken(hashedRefreshToken);
		userSession.setCreationTime(creationTime);
		userSession.setId(ulid.nextULID());
		return userSessionRepository.save(userSession);
	}

	private static ResponseCookie getResponseCookie(String refreshToken){
		return ResponseCookie.from(REFRESH_TOKEN, refreshToken)
				.httpOnly(true)
				.secure(false)
				.path("/auth/refresh")
				.maxAge(TokenUtil.REFRESH_TOKEN_MAX_AGE_SECONDS)
				.sameSite("Lax")
				.build();
	}

	@PostMapping("/refresh")
	@Transactional
	public ResponseEntity<TokenResponse> refresh(@CookieValue(value = REFRESH_TOKEN, required = false) String refreshToken){
		if(refreshToken == null){
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		String hashedRefreshToken = TokenUtil.hash(refreshToken);
		TokenEntity cachedToken = (TokenEntity) redisOperations.opsForValue().get(REFRESH_TOKEN_PREFIX + hashedRefreshToken);
		if(cachedToken != null){
			TokenResponse tokenResponse = TokenResponse.builder()
					.accessToken(cachedToken.token())
					.message("Token refreshed")
					.build();
			return ResponseEntity.ok(tokenResponse);
		}
		UserSession userSession = userSessionRepository.findByRefreshToken(hashedRefreshToken).orElse(null);
		if(userSession == null){
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		Instant now = Instant.now();
		if(userSession.getExpirationTime().isBefore(now)){
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		Thread.startVirtualThread(() -> extendToken(userSession.getId(), now));
		String newAccessToken = TokenUtil.generateJwtToken(userSession.getUsername(), userSession.getId());
		cacheAccessToken(hashedRefreshToken, newAccessToken, userSession);
		TokenResponse tokenResponse = TokenResponse.builder()
				.accessToken(newAccessToken)
				.message("Token refreshed")
				.build();
		return ResponseEntity.ok(tokenResponse);
	}

	private void extendToken(String id, Instant now){
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.executeWithoutResult(_ -> {
			UserSession userSession = userSessionRepository.findById(id).orElse(null);
			if(userSession == null){
				return;
			}
			log.info("Refreshing token for user {}", userSession.getUsername());
			userSession.setExpirationTime(now.plusSeconds(TokenUtil.REFRESH_TOKEN_MAX_AGE_SECONDS));
			userSessionRepository.save(userSession);
		});
	}

	@PostMapping("/logout")
	@Transactional
	public ResponseEntity<?> logout(Authentication authentication, HttpServletResponse response){
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
		ResponseCookie deleteCookie = ResponseCookie.from(REFRESH_TOKEN, null)
				.path("/auth/refresh")
				.httpOnly(true)
				.maxAge(0)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());
		return ResponseEntity.ok(TokenResponse.builder().message("Logged out").build());
	}

}
