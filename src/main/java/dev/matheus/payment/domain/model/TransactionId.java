package dev.matheus.payment.domain.model;

import java.util.Objects;
import java.util.UUID;
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
public final class TransactionId {

    private final UUID value;

    public static TransactionId generate() {
        return of(UuidV7.generate());
    }

    public static TransactionId of(UUID value) {
        Objects.requireNonNull(value, "transaction id is required");
        if (value.version() != 7) {
            throw new IllegalArgumentException("transaction id must be UUID v7, got version " + value.version());
        }
        return new TransactionId(value);
    }

    public static TransactionId of(String value) {
        return of(UUID.fromString(value));
    }
}
