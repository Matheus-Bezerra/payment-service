package dev.matheus.payment.domain.model;

import java.util.Objects;

public final class IdempotencyKey {

    private final String value;

    private IdempotencyKey(String value) {
        this.value = value;
    }

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

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdempotencyKey that)) {
            return false;
        }
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
