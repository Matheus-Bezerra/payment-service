package dev.matheus.payment.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class WalletId {

    private final UUID value;

    private WalletId(UUID value) {
        this.value = Objects.requireNonNull(value, "wallet id is required");
        if (value.version() != 7) {
            throw new IllegalArgumentException("wallet id must be UUID v7, got version " + value.version());
        }
    }

    public static WalletId generate() {
        return new WalletId(UuidV7.generate());
    }

    public static WalletId of(UUID value) {
        return new WalletId(value);
    }

    public static WalletId of(String value) {
        return of(UUID.fromString(value));
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WalletId walletId)) {
            return false;
        }
        return value.equals(walletId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
