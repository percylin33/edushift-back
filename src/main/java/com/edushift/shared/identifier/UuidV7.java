package com.edushift.shared.identifier;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Factory for <a href="https://datatracker.ietf.org/doc/rfc9562/">RFC 9562</a>
 * UUIDv7 (time-ordered, k-sortable) values.
 * <p>
 * Layout (128 bits):
 * <pre>
 *   |  48 bits unix_ts_ms  | 4 bits ver=7 | 12 bits rand_a | 2 bits var=10 | 62 bits rand_b |
 * </pre>
 * Properties relevant for EduShift:
 * <ul>
 *   <li><strong>Microservice-ready</strong>: globally unique without coordination</li>
 *   <li><strong>Index-friendly</strong>: monotonic per millisecond → good B-tree locality</li>
 *   <li><strong>Embedded timestamp</strong>: creation time recoverable via
 *       {@link #extractTimestamp(UUID)}</li>
 *   <li><strong>Safe to expose</strong>: cannot be enumerated like sequential ids</li>
 * </ul>
 */
public final class UuidV7 {

	private static final SecureRandom RANDOM = new SecureRandom();

	private UuidV7() {
	}

	public static UUID create() {
		long timestamp = System.currentTimeMillis() & 0xFFFFFFFFFFFFL;
		long randA = RANDOM.nextInt(0x1000);
		long msb = (timestamp << 16) | (0x7L << 12) | randA;

		long randB = RANDOM.nextLong();
		long lsb = (randB & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

		return new UUID(msb, lsb);
	}

	/**
	 * Extracts the embedded creation timestamp from a UUIDv7.
	 *
	 * @throws IllegalArgumentException when {@code uuid} is null or not version 7
	 */
	public static Instant extractTimestamp(UUID uuid) {
		if (uuid == null || uuid.version() != 7) {
			throw new IllegalArgumentException("Not a UUIDv7: " + uuid);
		}
		long ms = (uuid.getMostSignificantBits() >>> 16) & 0xFFFFFFFFFFFFL;
		return Instant.ofEpochMilli(ms);
	}

}
