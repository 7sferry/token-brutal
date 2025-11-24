package org.example.tokenbrutal.tools;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

@Component
public class TimestampInvoiceGenerator {
	private final String instanceId;
	private final AtomicInteger counter = new AtomicInteger(0);
	private final DateTimeFormatter formatter =
			DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	public TimestampInvoiceGenerator() {
		// Use machine identifier for distributed systems
		this.instanceId = getInstanceIdentifier();
	}

	public String generateInvoiceId() {
		String timestamp = LocalDateTime.now().format(formatter);
		int seq = counter.incrementAndGet() % 1000; // Reset after 999
		return String.format("INV-%s-%s-%03d", timestamp, instanceId, seq);
		// Example: INV-20240520143015-APP1-001
	}

	private String getInstanceIdentifier() {
		try {
			String hostname = InetAddress.getLocalHost().getHostName();
			return hostname.substring(Math.max(0, hostname.length() - 4));
		} catch (Exception e) {
			return "0001"; // Fallback
		}
	}
}
