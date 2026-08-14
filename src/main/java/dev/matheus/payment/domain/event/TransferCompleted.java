package dev.matheus.payment.domain.event;

import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.TransactionId;
import dev.matheus.payment.domain.model.UserId;
import java.time.Instant;
import java.util.Objects;

public final class TransferCompleted implements DomainEvent {

    private final TransactionId transactionId;
    private final UserId payerId;
    private final UserId payeeId;
    private final Money amount;
    private final Instant occurredAt;

    public TransferCompleted(
            TransactionId transactionId,
            UserId payerId,
            UserId payeeId,
            Money amount,
            Instant occurredAt
    ) {
        this.transactionId = Objects.requireNonNull(transactionId);
        this.payerId = Objects.requireNonNull(payerId);
        this.payeeId = Objects.requireNonNull(payeeId);
        this.amount = Objects.requireNonNull(amount);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    public TransactionId transactionId() {
        return transactionId;
    }

    public UserId payerId() {
        return payerId;
    }

    public UserId payeeId() {
        return payeeId;
    }

    public Money amount() {
        return amount;
    }

    @Override
    public Instant occurredAt() {
        return occurredAt;
    }
}
