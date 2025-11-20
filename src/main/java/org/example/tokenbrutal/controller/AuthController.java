package org.example.tokenbrutal.controller;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import org.example.tokenbrutal.util.JwtUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
		// For demo: hardcoded user
		if ("admin".equals(request.getUsername()) && "12345".equals(request.getPassword())) {
			String token = JwtUtil.generateToken(request.getUsername());

			ResponseCookie cookie = ResponseCookie.from("token", token)
					.httpOnly(true)
					.secure(false)
					.path("/")
					.maxAge(15 * 60)
					.sameSite("Lax")
					.build();

			response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
			return ResponseEntity.ok("Login success!");
		} else {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
		}
	}

	@GetMapping("/logout")
	public ResponseEntity<?> logout(HttpServletResponse response) {
		Cookie cookie = new Cookie("token", "");
		cookie.setPath("/");
		cookie.setHttpOnly(true);
		cookie.setMaxAge(0);
		response.addCookie(cookie);
		return ResponseEntity.ok("Logged out");
	}

	@Data
	public static class LoginRequest {
		private String username;
		private String password;
		// getters and setters
	}

}
