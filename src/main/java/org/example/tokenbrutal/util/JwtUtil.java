package org.example.tokenbrutal.util;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;

public class JwtUtil {
	private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor("supersecretkey12345678901234567890".getBytes());
	private static final long EXPIRATION = 1000 * 60 * 15; // 15 min

	public static String generateToken(String username) {
		return Jwts.builder()
				.subject(username)
				.expiration(new Date(System.currentTimeMillis() + EXPIRATION))
				.signWith(SECRET_KEY)
				.compact();
	}

	public static String validateToken(String token) {
		try {
			return Jwts.parser()
					.verifyWith(SECRET_KEY)
					.build()
					.parseSignedClaims(token)
					.getPayload()
					.getSubject();
		} catch (Exception e) {
			return null;
		}
	}
}
