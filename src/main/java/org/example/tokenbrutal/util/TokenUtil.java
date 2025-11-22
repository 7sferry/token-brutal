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
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

public class TokenUtil{
	private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor("supersecretkey12345678901234567890".getBytes());
	private static final long EXPIRATION = 1000 * 60 * 2; // 15 min
	private static final SecureRandom random = new SecureRandom();
	private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder();

	public static String generateJwtToken(String username, String sessionId) {
		Map<String, String> claims = Maps.of("role", "superuser").and("realName", "ferry").and("sessionId", sessionId).build();
		return Jwts.builder()
				.subject(username)
				.claims(claims)
				.expiration(new Date(System.currentTimeMillis() + EXPIRATION))
				.signWith(SECRET_KEY)
				.compact();
	}

	public static Jws<Claims> parseJwtToken(String token) {
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

	public static String generateOpaqueToken(){
		byte[] bytes = new byte[20];
		random.nextBytes(bytes);
		return base64Encoder.encodeToString(bytes);
	}

}
