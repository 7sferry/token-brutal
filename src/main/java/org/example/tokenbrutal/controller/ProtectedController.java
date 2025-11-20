package org.example.tokenbrutal.controller;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.tokenbrutal.util.JwtUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api")
public class ProtectedController {

	@GetMapping("/profile")
	public String getProfile(HttpServletRequest request) {
		log.info("Getting profile");
		String token = null;
		if (request.getCookies() != null) {
			for (Cookie c : request.getCookies()) {
				if ("token".equals(c.getName())) {
					token = c.getValue();
					break;
				}
			}
		}

		String username = JwtUtil.validateToken(token);
		if (username == null) {
			throw new RuntimeException("Invalid or expired token");
		}

		return "Welcome, " + username + "! This is your profile.";
	}
}
