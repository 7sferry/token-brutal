package org.example.tokenbrutal.util;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.lang.Maps;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

public class JwtUtil {
	private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor("supersecretkey12345678901234567890".getBytes());
	private static final long EXPIRATION = 1000 * 60 * 2; // 15 min

	public static String generateToken(String username) {
		Map<String, String> claims = Maps.of("role", "superuser").and("realName", "ferry").build();
		return Jwts.builder()
				.subject(username)
				.claims(claims)
				.expiration(new Date(System.currentTimeMillis() + EXPIRATION))
				.signWith(SECRET_KEY)
				.compact();
	}

	public static Jws<Claims> validateToken(String token) {
		if(token == null || token.isBlank()){
			return null;
		}
		try {
			return Jwts.parser()
					.verifyWith(SECRET_KEY)
					.build()
					.parseSignedClaims(token);
		} catch (Exception e) {
			return null;
		}
	}
}
