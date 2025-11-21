package org.example.tokenbrutal.controller;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class ProtectedController {

	@GetMapping("/profile")
	public String getProfile(Authentication authentication) {
		log.info("Getting profile");
		Claims principal = (Claims) authentication.getPrincipal();
		return "🔐Welcome, " + principal.getSubject() + "! This is your profile.";
	}
}
