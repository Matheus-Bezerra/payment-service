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
public final class WalletId {

    private final UUID value;

    public static WalletId generate() {
        return of(UuidV7.generate());
    }

    public static WalletId of(UUID value) {
        Objects.requireNonNull(value, "wallet id is required");
        if (value.version() != 7) {
            throw new IllegalArgumentException("wallet id must be UUID v7, got version " + value.version());
        }
        return new WalletId(value);
    }

    public static WalletId of(String value) {
        return of(UUID.fromString(value));
    }
}
