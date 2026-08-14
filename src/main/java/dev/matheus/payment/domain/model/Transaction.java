package dev.matheus.payment.domain.model;

import dev.matheus.payment.domain.enums.TransactionStatus;
import dev.matheus.payment.domain.event.DomainEvent;
import dev.matheus.payment.domain.event.TransferCompleted;
import dev.matheus.payment.domain.exception.InvalidTransactionStateException;
import dev.matheus.payment.domain.exception.InvalidTransferAmountException;
import dev.matheus.payment.domain.exception.SameAccountTransferException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class Transaction {

    private final TransactionId id;
    private final IdempotencyKey idempotencyKey;
    private final UserId payerId;
    private final UserId payeeId;
    private final Money amount;
    private TransactionStatus status;
    private String failureReason;
    private final Instant createdAt;
    private Instant completedAt;

    @Getter(AccessLevel.NONE)
    private final List<DomainEvent> events = new ArrayList<>();

    private Transaction(
            TransactionId id,
            IdempotencyKey idempotencyKey,
            UserId payerId,
            UserId payeeId,
            Money amount,
            TransactionStatus status,
            String failureReason,
            Instant createdAt,
            Instant completedAt
    ) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.payerId = payerId;
        this.payeeId = payeeId;
        this.amount = amount;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public static Transaction start(
            UserId payerId,
            UserId payeeId,
            Money amount,
            IdempotencyKey idempotencyKey
    ) {
        Objects.requireNonNull(payerId, "payer id is required");
        Objects.requireNonNull(payeeId, "payee id is required");
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(idempotencyKey, "idempotency key is required");

        if (payerId.equals(payeeId)) {
            throw new SameAccountTransferException("payer and payee must be different");
        }
        if (amount.amount().signum() <= 0) {
            throw new InvalidTransferAmountException("transfer amount must be greater than zero");
        }

        return new Transaction(
                TransactionId.generate(),
                idempotencyKey,
                payerId,
                payeeId,
                amount,
                TransactionStatus.IN_PROGRESS,
                null,
                Instant.now(),
                null
        );
    }

    public void complete() {
        if (status != TransactionStatus.IN_PROGRESS && status != TransactionStatus.AUTHORIZED) {
            throw new InvalidTransactionStateException(
                    "cannot complete transaction in status " + status
            );
        }
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.events.add(new TransferCompleted(id, payerId, payeeId, amount, completedAt));
    }

    public void fail(String reason) {
        if (status != TransactionStatus.IN_PROGRESS) {
            throw new InvalidTransactionStateException(
                    "cannot fail transaction in status " + status
            );
        }
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }

    public boolean matchesPayload(UserId payerId, UserId payeeId, Money amount) {
        return this.payerId.equals(payerId)
                && this.payeeId.equals(payeeId)
                && this.amount.equals(amount);
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> drained = List.copyOf(events);
        events.clear();
        return drained;
    }
}
