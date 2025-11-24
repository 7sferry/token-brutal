package org.example.tokenbrutal.tools;

import java.util.concurrent.atomic.AtomicLong;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

public class DistributedInvoiceGenerator {
	private final long instanceId;
	private final long epoch = 1609459200000L; // Custom epoch (2021-01-01)
	private final AtomicLong counter = new AtomicLong(0);

	public DistributedInvoiceGenerator(long instanceId) {
		this.instanceId = instanceId & 0x3FF; // 10 bits for instance ID
	}

	public String generateInvoiceId() {
		long timestamp = System.currentTimeMillis() - epoch;
		long sequence = counter.incrementAndGet() & 0xFFF; // 12 bits for sequence

		long snowflakeId = (timestamp << 22) | (instanceId << 12) | sequence;

		return "INV-" + snowflakeId;
		// Example: INV-387257753600001
	}
}
