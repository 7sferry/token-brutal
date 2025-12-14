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
import java.util.Date;
import java.util.Map;

public class TokenUtil{
	public static final int REFRESH_TOKEN_MAX_AGE_SECONDS = 10 * 60;
	public static final int ROTATION_TOKEN_BEFORE_EXPIRE_IN_SECONDS = 3 * 60;
	public static final long ACCESS_TOKEN_EXPIRATION_MS = 2 * 60 * 1000;
	private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor("supersecretkey12345678901234567890".getBytes());
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

	public static String generateJwtToken(String username) {
		Map<String, String> claims = Maps.of("role", "superuser").and("realName", "ferry").build();
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

	public static String generateOpaqueToken(long expirationEpoch){
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return CrockfordBase32.encodeTimestamp(expirationEpoch) + CrockfordBase32.encode(bytes);
	}

	@SneakyThrows
	public static String hashOpaqueToken(String refreshToken){
		String uniquePart = refreshToken.substring(10);
		MessageDigest sha256 = MessageDigest.getInstance("SHA256");
		byte[] digest = sha256.digest(uniquePart.getBytes());
		return refreshToken.substring(0, 10) + CrockfordBase32.encode(digest);
	}

}
