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
import lombok.SneakyThrows;

import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

public class TokenUtil{
	public static final int REFRESH_TOKEN_MAX_AGE_SECONDS = 5 * 60;
	public static final long ACCESS_TOKEN_EXPIRATION_MS = 2 * 60 * 1000; // 15 min
	private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor("supersecretkey12345678901234567890".getBytes());
	private static final Base64.Encoder BASE_64_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final SecureRandom SECURE_RANDOM;

	static{
		SecureRandom instanceStrong;
		try{
			instanceStrong = SecureRandom.getInstanceStrong();
		} catch(NoSuchAlgorithmException e){
			instanceStrong = new SecureRandom();
		}
		SECURE_RANDOM = instanceStrong;
	}

	public static String generateJwtToken(String username, String sessionId) {
		Map<String, String> claims = Maps.of("role", "superuser").and("realName", "ferry").and("sessionId", sessionId).build();
		return Jwts.builder()
				.subject(username)
				.claims(claims)
				.expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION_MS))
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
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return BASE_64_ENCODER.encodeToString(bytes);
	}

	@SneakyThrows
	public static String hash(String value){
		MessageDigest sha256 = MessageDigest.getInstance("SHA256");
		byte[] digest = sha256.digest(value.getBytes());
		return BASE_64_ENCODER.encodeToString(digest);
	}

}
