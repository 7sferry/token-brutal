package org.example.tokenbrutal.controller;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

import de.huxhorn.sulky.ulid.ULID;
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
	public static final String REFRESH_TOKEN_PREFIX = "refreshToken:";
	public static final String REFRESH_TOKEN = "refresh_token";
	public static final String REFRESH = "/auth";

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
			UserSession userSession = saveSession(hashedRefreshToken, request.username());
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

	private UserSession saveSession(String hashedRefreshToken, String username){
		UserSession userSession = new UserSession();
		userSession.setUsername(username);
		Instant creationTime = Instant.now();
		userSession.setExpirationTime(creationTime.plusSeconds(TokenUtil.REFRESH_TOKEN_MAX_AGE_SECONDS));
		userSession.setRefreshToken(hashedRefreshToken);
		userSession.setId(ulid.nextULID());
		return userSessionRepository.save(userSession);
	}

	private static ResponseCookie getResponseCookie(String refreshToken){
		return ResponseCookie.from(REFRESH_TOKEN, refreshToken)
				.httpOnly(true)
				.secure(false)
				.path(REFRESH)
				.maxAge(TokenUtil.REFRESH_TOKEN_MAX_AGE_SECONDS)
				.sameSite("Lax")
				.build();
	}

	@PostMapping("/refresh")
	@Transactional
	public ResponseEntity<TokenResponse> refresh(@CookieValue(REFRESH_TOKEN) String refreshToken, HttpServletResponse response){
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
		String username = userSession.getUsername();
		String accessToken = TokenUtil.generateJwtToken(username, userSession.getId());
		String newRefreshToken = TokenUtil.generateOpaqueToken();
		Thread.startVirtualThread(() -> rotateNewToken(newRefreshToken, username, accessToken, now, hashedRefreshToken));
		ResponseCookie cookie = getResponseCookie(newRefreshToken);
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
		TokenResponse tokenResponse = TokenResponse.builder()
				.accessToken(accessToken)
				.message("Login successful")
				.build();
		return ResponseEntity.ok(tokenResponse);
	}

	private void rotateNewToken(String newRefreshToken, String username, String accessToken, Instant now, String oldHashedRefreshToken){
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.executeWithoutResult(_ -> {
			String newHashedRefreshToken = TokenUtil.hash(newRefreshToken);
			UserSession newUserSession = saveSession(newHashedRefreshToken, username);
			cacheAccessToken(newHashedRefreshToken, accessToken, newUserSession);
			userSessionRepository.findByRefreshToken(oldHashedRefreshToken)
					.ifPresent(userSession -> {
						userSession.setExpirationTime(now.plus(Duration.ofMinutes(1)));
						userSessionRepository.save(userSession);
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
	public ResponseEntity<?> logout(@CookieValue(REFRESH_TOKEN) String refreshToken, HttpServletResponse response){
		Instant now = Instant.now();
		String hashedRefreshToken = TokenUtil.hash(refreshToken);
		userSessionRepository.findByRefreshToken(hashedRefreshToken)
				.filter(userSession -> userSession.getExpirationTime().isAfter(now))
				.ifPresent(user -> {
					user.setExpirationTime(now);
					userSessionRepository.save(user);
				});
		redisOperations.delete(REFRESH_TOKEN_PREFIX + hashedRefreshToken);
		ResponseCookie deleteCookie = ResponseCookie.from(REFRESH_TOKEN, null)
				.path(REFRESH)
				.httpOnly(true)
				.maxAge(0)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());
		return ResponseEntity.ok(TokenResponse.builder().message("Logged out").build());
	}

}
