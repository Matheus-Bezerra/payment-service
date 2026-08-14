package dev.matheus.payment.domain.model;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * UUID version 7 (RFC 9562). Java 25 does not ship {@code UUID.ofEpochMillis}; Java 26 does.
 * This mirrors that algorithm so PKs stay time-ordered without {@code UUID.randomUUID()} (v4).
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        return ofEpochMillis(Instant.now().toEpochMilli());
    }

    public static UUID ofEpochMillis(long timestamp) {
        if ((timestamp >> 48) != 0) {
            throw new IllegalArgumentException("timestamp does not fit within 48 bits: " + timestamp);
        }

        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);

        bytes[0] = (byte) (timestamp >>> 40);
        bytes[1] = (byte) (timestamp >>> 32);
        bytes[2] = (byte) (timestamp >>> 24);
        bytes[3] = (byte) (timestamp >>> 16);
        bytes[4] = (byte) (timestamp >>> 8);
        bytes[5] = (byte) timestamp;

        bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x70);
        bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);

        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xff);
        }
        return new UUID(msb, lsb);
    }
}
