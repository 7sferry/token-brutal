package org.example.tokenbrutal.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/************************
 * Made by [MR Ferry™]  *
 * on Desember 2025     *
 ************************/

public class CrockfordBase32 {
	private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
	private static final Map<Character, Integer> DECODE_MAP;

	static {
		Map<Character, Integer> map = new HashMap<>();
		for (int i = 0; i < ALPHABET.length(); i++) {
			map.put(ALPHABET.charAt(i), i);
		}

		map.put('O', 0);
		map.put('o', 0);
		map.put('I', 1);
		map.put('i', 1);
		map.put('L', 1);
		map.put('l', 1);
		DECODE_MAP = Collections.unmodifiableMap(map);
	}

	public static String encodeTimestamp(long timestamp) {
		char[] out = new char[10];

		// 48-bit timestamp → 10 chars
		long value = timestamp;
		for (int i = 9; i >= 0; i--) {
			out[i] = ALPHABET.charAt((int) (value & 0x1F));
			value >>= 5;
		}
		return new String(out);
	}

	public static String encode(byte[] data) {
		if (data == null || data.length == 0) {
			return "";
		}

		StringBuilder result = new StringBuilder();
		int buffer = 0;
		int bitsLeft = 0;

		for (byte b : data) {
			buffer = (buffer << 8) | (b & 0xFF);
			bitsLeft += 8;

			while (bitsLeft >= 5) {
				int index = (buffer >>> (bitsLeft - 5)) & 0x1F;
				result.append(ALPHABET.charAt(index));
				bitsLeft -= 5;
			}
		}

		// Handle remaining bits
		if (bitsLeft > 0) {
			int index = (buffer << (5 - bitsLeft)) & 0x1F;
			result.append(ALPHABET.charAt(index));
		}

		return result.toString();
	}

	public static byte[] decode(String encoded) {
		if (encoded == null || encoded.isEmpty()) {
			return new byte[0];
		}

		// Remove hyphens and convert to uppercase
		String cleaned = encoded.replace("-", "").toUpperCase();

		int buffer = 0;
		int bitsLeft = 0;
		int count = 0;

		// Calculate output length
		byte[] temp = new byte[cleaned.length() * 5 / 8];

		for (char c : cleaned.toCharArray()) {
			Integer value = DECODE_MAP.get(c);
			if (value == null) {
				throw new IllegalArgumentException("Invalid character in encoded string: " + c);
			}

			buffer = (buffer << 5) | value;
			bitsLeft += 5;

			if (bitsLeft >= 8) {
				temp[count++] = (byte) ((buffer >>> (bitsLeft - 8)) & 0xFF);
				bitsLeft -= 8;
			}
		}

		// Validate no trailing bits that would cause ambiguous padding
		if (bitsLeft >= 5) {
			throw new IllegalArgumentException("Invalid padding in encoded string");
		}

		if ((buffer & ((1 << bitsLeft) - 1)) != 0) {
			throw new IllegalArgumentException("Non-zero trailing bits in encoded string");
		}

		byte[] result = new byte[count];
		System.arraycopy(temp, 0, result, 0, count);
		return result;
	}

	public static String encodeNumber(long value) {
		if (value < 0) {
			throw new IllegalArgumentException("Value must be non-negative");
		}
		if (value == 0) {
			return "0";
		}

		char[] buffer = new char[13]; // max chars for 64-bit value (ceil(64/5)=13)
		int pos = buffer.length;

		while (value > 0) {
			int index = (int)(value & 0x1F); // take 5 bits
			buffer[--pos] = ALPHABET.charAt(index);
			value >>>= 5;
		}

		return new String(buffer, pos, buffer.length - pos);
	}

	public static long decodeNumber(String encoded) {
		if (encoded == null || encoded.isEmpty() || encoded.length() > 13) {
			throw new IllegalArgumentException("invalid string " + encoded);
		}

		long result = 0;

		for (int i = 0; i < encoded.length(); i++) {
			char c = encoded.charAt(i);

			Integer value = DECODE_MAP.get(Character.toUpperCase(c));

			if (value == null) {
				throw new IllegalArgumentException("Invalid character in encoded string: " + c);
			}

			// Shift the running result by 5 bits to the left
			// This prepares space for the new 5-bit value.
			result <<= 5;

			// Add the character's 5-bit value to the result using bitwise OR
			result |= value;
		}

		return result;
	}

}
