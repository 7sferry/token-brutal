package org.example.tokenbrutal.controller;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.tokenbrutal.controller.request.LoginRequest;
import org.example.tokenbrutal.controller.response.LoginResponse;
import org.example.tokenbrutal.controller.response.RefreshTokenResponse;
import org.example.tokenbrutal.persistent.UserSession;
import org.example.tokenbrutal.repository.UserSessionRepository;
import org.example.tokenbrutal.util.JwtUtil;
import org.example.tokenbrutal.util.OpaqueTokenUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
	public static final int MAX_AGE_SECONDS = 5 * 60;

	private final UserSessionRepository userSessionRepository;

	@PostMapping("/login")
	@Transactional
	public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
		// For demo: hardcoded user
		if ("admin".equals(request.username()) && "12345".equals(request.password())) {
			String refreshToken = OpaqueTokenUtil.generateToken();
			ResponseCookie cookie = getResponseCookie(refreshToken);
			response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
			saveSession(request, refreshToken);
			String token = JwtUtil.generateToken(request.username());
			LoginResponse loginResponse = LoginResponse.builder()
					.accessToken(token)
					.message("Login success!")
					.build();
			return ResponseEntity.ok(loginResponse);
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}

	private void saveSession(LoginRequest request, String refreshToken){
		UserSession userSession = new UserSession();
		userSession.setUsername(request.username());
		Instant creationTime = Instant.now();
		userSession.setExpirationTime(creationTime.plusSeconds(MAX_AGE_SECONDS));
		userSession.setSessionId(refreshToken);
		userSession.setCreationTime(creationTime);
		userSessionRepository.save(userSession);
	}

	private static ResponseCookie getResponseCookie(String refreshToken){
		return ResponseCookie.from("refresh_token", refreshToken)
				.httpOnly(true)
				.secure(false)
				.path("/auth/refresh")
				.maxAge(MAX_AGE_SECONDS)
				.sameSite("Lax")
				.build();
	}

	@PostMapping("/refresh")
	public ResponseEntity<RefreshTokenResponse> refresh(@CookieValue(value = "refresh_token", required = false) String refreshToken) {
		if (refreshToken == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		UserSession userSession = userSessionRepository.findById(refreshToken).orElse(null);
		if (userSession == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		if(userSession.getExpirationTime().isBefore(Instant.now())){
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		String username = userSession.getUsername();
		String newAccessToken = JwtUtil.generateToken(username);
		RefreshTokenResponse tokenResponse = RefreshTokenResponse.builder()
				.accessToken(newAccessToken)
				.username(username)
				.build();
		return ResponseEntity.ok(tokenResponse);
	}

	@GetMapping("/logout")
	public ResponseEntity<?> logout(HttpServletResponse response) {
		Cookie cookie = new Cookie("refresh_token", "");
		cookie.setPath("/");
		cookie.setHttpOnly(true);
		cookie.setMaxAge(0);
		response.addCookie(cookie);
		return ResponseEntity.ok("Logged out");
	}

}
