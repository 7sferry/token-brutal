package org.example.tokenbrutal.controller;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

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
	public static final String REFRESH_TOKEN_KEY_PREFIX = "refreshToken:";
	public static final String REFRESH_TOKEN = "refresh_token";
	public static final String AUTH_COOKIE_PATH = "/auth";

	private final UserSessionRepository userSessionRepository;
	private final RedisOperations<String, Object> redisOperations;
	private final PlatformTransactionManager transactionManager;

	@PostMapping("/login")
	@Transactional
	public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request, HttpServletResponse response){
		if("admin".equals(request.username()) && "12345".equals(request.password())){
			Instant expirationTime = Instant.now().plusSeconds(TokenUtil.REFRESH_TOKEN_MAX_AGE_SECONDS);
			String refreshToken = TokenUtil.generateOpaqueToken(expirationTime.toEpochMilli());
			String hashedRefreshToken = TokenUtil.hashOpaqueToken(refreshToken);
			UserSession userSession = saveSession(hashedRefreshToken, request.username(), expirationTime);
			String accessToken = TokenUtil.generateJwtToken(request.username());
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
		redisOperations.opsForValue().set(getHashedRefreshTokenKey(hashedRefreshToken), tokenEntity, Duration.ofMillis(TokenUtil.ACCESS_TOKEN_EXPIRATION_MS));
	}

	private static String getHashedRefreshTokenKey(String hashedRefreshToken){
		return REFRESH_TOKEN_KEY_PREFIX + hashedRefreshToken;
	}

	private UserSession saveSession(String hashedRefreshToken, String username, Instant expirationTime){
		UserSession userSession = new UserSession();
		userSession.setRefreshToken(hashedRefreshToken);
		userSession.setUsername(username);
		userSession.setExpirationTime(expirationTime);
		return userSessionRepository.save(userSession);
	}

	private static ResponseCookie getResponseCookie(String refreshToken){
		return ResponseCookie.from(REFRESH_TOKEN, refreshToken)
				.httpOnly(true)
				.secure(false)
				.path(AUTH_COOKIE_PATH)
				.maxAge(TokenUtil.REFRESH_TOKEN_MAX_AGE_SECONDS)
				.sameSite("Lax")
				.build();
	}

	@PostMapping("/refresh")
	@Transactional
	public ResponseEntity<TokenResponse> refresh(@CookieValue(value = REFRESH_TOKEN, required = false) String refreshToken, HttpServletResponse response){
		if(refreshToken == null){
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		String hashedRefreshToken = TokenUtil.hashOpaqueToken(refreshToken);
		TokenEntity cachedToken = (TokenEntity) redisOperations.opsForValue().get(getHashedRefreshTokenKey(hashedRefreshToken));
		if(cachedToken != null){
			TokenResponse tokenResponse = TokenResponse.builder()
					.accessToken(cachedToken.token())
					.message("Token refreshed")
					.build();
			return ResponseEntity.ok(tokenResponse);
		}
		UserSession userSession = userSessionRepository.findById(hashedRefreshToken).orElse(null);
		if(userSession == null){
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		Instant now = Instant.now();
		Instant expirationTime = userSession.getExpirationTime();
		if(expirationTime.isBefore(now)){
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		String username = userSession.getUsername();
		String accessToken = TokenUtil.generateJwtToken(username);
		if(expirationTime.minusSeconds(TokenUtil.ROTATION_TOKEN_BEFORE_EXPIRE_IN_SECONDS).isAfter(now)){
			cacheAccessToken(hashedRefreshToken, accessToken, userSession);
			TokenResponse tokenResponse = TokenResponse.builder()
					.accessToken(accessToken)
					.message("Token refreshed")
					.build();
			return ResponseEntity.ok(tokenResponse);
		}
		String newRefreshToken = getNewRefreshToken(username, accessToken, hashedRefreshToken, now);
		ResponseCookie cookie = getResponseCookie(newRefreshToken);
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
		TokenResponse tokenResponse = TokenResponse.builder()
				.accessToken(accessToken)
				.message("Token refreshed")
				.build();
		return ResponseEntity.ok(tokenResponse);
	}

	private String getNewRefreshToken(String username, String accessToken, String hashedRefreshToken, Instant now){
		Instant expirationTime = now.plusSeconds(TokenUtil.REFRESH_TOKEN_MAX_AGE_SECONDS);
		String newRefreshToken = TokenUtil.generateOpaqueToken(expirationTime.toEpochMilli());
		Thread.startVirtualThread(() -> rotateNewToken(newRefreshToken, username, accessToken, hashedRefreshToken, expirationTime));
		return newRefreshToken;
	}

	private void rotateNewToken(String newRefreshToken, String username, String accessToken, String oldHashedRefreshToken, Instant expirationTime){
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.executeWithoutResult(_ -> {
			String newHashedRefreshToken = TokenUtil.hashOpaqueToken(newRefreshToken);
			UserSession newUserSession = saveSession(newHashedRefreshToken, username, expirationTime);
			cacheAccessToken(newHashedRefreshToken, accessToken, newUserSession);
			userSessionRepository.findById(oldHashedRefreshToken)
					.ifPresent(userSession -> {
						Duration minutes = Duration.ofMinutes(1);
						Instant now = Instant.now();
						if(userSession.getExpirationTime().minus(minutes).isAfter(now)){
							userSession.setExpirationTime(now.plus(minutes));
							userSessionRepository.save(userSession);
						}
					});
		});
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
	public ResponseEntity<?> logout(@CookieValue(value = REFRESH_TOKEN, required = false)  String refreshToken, HttpServletResponse response){
		if(refreshToken == null){
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		Instant now = Instant.now();
		String hashedRefreshToken = TokenUtil.hashOpaqueToken(refreshToken);
		userSessionRepository.findById(hashedRefreshToken)
				.filter(userSession -> userSession.getExpirationTime().isAfter(now))
				.ifPresent(user -> {
					user.setExpirationTime(now);
					userSessionRepository.save(user);
				});
		redisOperations.delete(getHashedRefreshTokenKey(hashedRefreshToken));
		ResponseCookie deleteCookie = ResponseCookie.from(REFRESH_TOKEN, null)
				.path(AUTH_COOKIE_PATH)
				.httpOnly(true)
				.maxAge(0)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());
		return ResponseEntity.ok(TokenResponse.builder().message("Logged out").build());
	}

}
