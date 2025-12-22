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
@RequestMapping(AuthController.AUTH_PATH)
@Slf4j
public class AuthController{
	public static final String REFRESH_TOKEN_KEY_PREFIX = "refreshToken:";
	public static final String REFRESH_TOKEN = "refresh_token";
	public static final String AUTH_PATH = "/auth";

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
			cacheRefreshToken(hashedRefreshToken, userSession);
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

	private void cacheRefreshToken(String hashedRefreshToken, UserSession userSession){
		redisOperations.opsForValue().set(getHashedRefreshTokenKey(hashedRefreshToken), userSession, Duration.ofSeconds(TokenUtil.REFRESH_TOKEN_MAX_AGE_SECONDS));
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
				.path(AUTH_PATH)
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
		UserSession userSession = getUserSession(hashedRefreshToken);
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
			TokenResponse tokenResponse = TokenResponse.builder()
					.accessToken(accessToken)
					.message("Token refreshed")
					.build();
			return ResponseEntity.ok(tokenResponse);
		}
		String newRefreshToken = getNewRefreshToken(username, hashedRefreshToken, now);
		ResponseCookie cookie = getResponseCookie(newRefreshToken);
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
		TokenResponse tokenResponse = TokenResponse.builder()
				.accessToken(accessToken)
				.message("Token refreshed")
				.build();
		return ResponseEntity.ok(tokenResponse);
	}

	private UserSession getUserSession(String hashedRefreshToken){
		UserSession cachedToken = (UserSession) redisOperations.opsForValue().get(getHashedRefreshTokenKey(hashedRefreshToken));
		return cachedToken != null ? cachedToken : userSessionRepository.findById(hashedRefreshToken).orElse(null);
	}

	private String getNewRefreshToken(String username, String hashedRefreshToken, Instant now){
		Instant expirationTime = now.plusSeconds(TokenUtil.REFRESH_TOKEN_MAX_AGE_SECONDS);
		String newRefreshToken = TokenUtil.generateOpaqueToken(expirationTime.toEpochMilli());
		Thread.startVirtualThread(() -> rotateNewToken(newRefreshToken, username, hashedRefreshToken, expirationTime));
		return newRefreshToken;
	}

	private void rotateNewToken(String newRefreshToken, String username, String oldHashedRefreshToken, Instant expirationTime){
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.executeWithoutResult(_ -> {
			String newHashedRefreshToken = TokenUtil.hashOpaqueToken(newRefreshToken);
			UserSession newUserSession = saveSession(newHashedRefreshToken, username, expirationTime);
			cacheRefreshToken(newHashedRefreshToken, newUserSession);
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
				.path(AUTH_PATH)
				.httpOnly(true)
				.maxAge(0)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());
		return ResponseEntity.ok(TokenResponse.builder().message("Logged out").build());
	}

}
