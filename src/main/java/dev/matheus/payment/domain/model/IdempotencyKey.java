package dev.matheus.payment.domain.model;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class IdempotencyKey {

    private final String value;

    public static IdempotencyKey of(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("idempotency key is required");
        }
        String normalized = rawValue.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("idempotency key must be at most 100 characters");
        }
        return new IdempotencyKey(normalized);
    }
}
