package org.example.tokenbrutal.controller;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
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
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
	public static final int MAX_AGE_SECONDS = 24 * 60 * 60;

	private final UserSessionRepository userSessionRepository;

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
		// For demo: hardcoded user
		if ("admin".equals(request.username()) && "12345".equals(request.password())) {
			String token = JwtUtil.generateToken(request.username());
			LoginResponse loginResponse = LoginResponse.builder().accessToken(token).message("Login success!").build();
			String refreshToken = OpaqueTokenUtil.generateToken();
			ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
					.httpOnly(true)
					.secure(false)
					.path("/auth/refresh")
					.maxAge(MAX_AGE_SECONDS)
					.sameSite("Lax")
					.build();
			response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
			UserSession userSession = new UserSession();
			userSession.setUsername(request.username());
			userSession.setExpirationTime(Instant.now().plusSeconds(MAX_AGE_SECONDS));
			userSession.setSessionId(refreshToken);
			userSessionRepository.save(userSession);
			return ResponseEntity.ok(loginResponse);
		} else {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
		}
	}

	@PostMapping("/refresh")
	public ResponseEntity<RefreshTokenResponse> refresh(@CookieValue(value = "refresh_token", required = false) String refreshToken) {
		if (refreshToken == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		Jws<Claims> claimsJws = JwtUtil.validateToken(refreshToken);
		if (claimsJws == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		Claims payload = claimsJws.getPayload();
		if (payload == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		String username = payload.getSubject();
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
