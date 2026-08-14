package dev.matheus.payment.domain.model;

import java.util.Objects;

public final class Wallet {

    private final WalletId id;
    private final UserId ownerId;
    private Money balance;

    private Wallet(WalletId id, UserId ownerId, Money balance) {
        this.id = id;
        this.ownerId = ownerId;
        this.balance = balance;
    }

    public static Wallet create(WalletId id, UserId ownerId, Money balance) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(ownerId, "owner id is required");
        Objects.requireNonNull(balance, "balance is required");
        return new Wallet(id, ownerId, balance);
    }

    public void debit(Money amount) {
        Objects.requireNonNull(amount, "amount is required");
        this.balance = this.balance.subtract(amount);
    }

    public void credit(Money amount) {
        Objects.requireNonNull(amount, "amount is required");
        this.balance = this.balance.add(amount);
    }

    public WalletId id() {
        return id;
    }

    public UserId ownerId() {
        return ownerId;
    }

    public Money balance() {
        return balance;
    }
}
