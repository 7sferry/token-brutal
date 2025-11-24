package org.example.tokenbrutal.tools;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

public class UniquenessAnalyzer {
	private static final int TOKEN_BITS = 64;

	public static void calculateCollisionProbability(long tokensGenerated) {
		// Birthday paradox formula
		double probability = 1 - Math.exp(-Math.pow(tokensGenerated, 2) / (2 * Math.pow(2, TOKEN_BITS)));

		System.out.println("Tokens generated: " + tokensGenerated);
		System.out.println("Collision probability: " + probability);
		System.out.println("Expected collisions: " + (tokensGenerated * (tokensGenerated - 1)) / (2 * Math.pow(2, TOKEN_BITS + 1)));
	}

	static void main(String[] args) {
		calculateCollisionProbability(1_000_000);        // 1 million tokens
		calculateCollisionProbability(1_000_000_000L);   // 1 billion tokens
		calculateCollisionProbability(1_000_000_000_000L); // 1 trillion tokens
	}

}
