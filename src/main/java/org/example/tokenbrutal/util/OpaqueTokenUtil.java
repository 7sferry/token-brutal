package org.example.tokenbrutal.util;

import java.security.SecureRandom;
import java.util.Base64;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

public class OpaqueTokenUtil{
	private static final SecureRandom random = new SecureRandom();
	private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder(); // URL-safe

	public static String generateToken(){
		byte[] bytes = new byte[20];
		random.nextBytes(bytes);
		return base64Encoder.encodeToString(bytes);
	}
}
