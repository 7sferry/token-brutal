package org.example.tokenbrutal.tools;

import lombok.RequiredArgsConstructor;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

@RequiredArgsConstructor
public class TokenAlternative {
	private final SecureRandom secureRandom;
	private final String instanceId;
	private final AtomicLong sequence = new AtomicLong();

	public TokenAlternative() {
		this.secureRandom = initializeSecureRandom();
		this.instanceId = generateInstanceId();
	}

	private SecureRandom initializeSecureRandom() {
		try {
			SecureRandom sr = SecureRandom.getInstanceStrong();
			// Additional seeding
			sr.nextBytes(new byte[64]);
			return sr;
		} catch (NoSuchAlgorithmException e) {
			return new SecureRandom();
		}
	}

	public String generateToken() {
		// 16 bytes timestamp + sequence + instance
		// 16 bytes cryptographic random
		ByteBuffer buffer = ByteBuffer.allocate(32);

		// Deterministic part (unique per instance/sequence)
		buffer.putLong(System.currentTimeMillis());        // 8 bytes
		buffer.putLong(sequence.incrementAndGet());        // 8 bytes
		buffer.put(instanceId.getBytes(StandardCharsets.UTF_8), 0, 4); // 4 bytes

		// Random part
		byte[] randomBytes = new byte[12];
		secureRandom.nextBytes(randomBytes);
		buffer.put(randomBytes);                           // 12 bytes

		return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
	}

	private String generateInstanceId() {
		try {
			// Use machine-specific identifier
			String hostname = InetAddress.getLocalHost().getHostName();
			return Integer.toHexString(hostname.hashCode()).substring(0, 4);
		} catch (Exception e) {
			return UUID.randomUUID().toString().substring(0, 4);
		}
	}

}
