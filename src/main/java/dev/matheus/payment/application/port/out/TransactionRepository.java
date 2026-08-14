package dev.matheus.payment.application.port.out;

import dev.matheus.payment.application.exception.DuplicateIdempotencyKeyException;
import dev.matheus.payment.domain.model.IdempotencyKey;
import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.Transaction;
import dev.matheus.payment.domain.model.TransactionId;
import dev.matheus.payment.domain.model.UserId;
import java.time.Instant;

public interface TransactionRepository {

    /**
     * Inserts a new {@code IN_PROGRESS} row. On unique violation of idempotency_key,
     * throws {@link DuplicateIdempotencyKeyException}.
     */
    void insertInProgress(Transaction transaction);

    Transaction requireByIdempotencyKeyForUpdate(IdempotencyKey idempotencyKey);

    void update(Transaction transaction);

    Money sumPayerCompletedOrInProgressToday(
            UserId payerId,
            Instant dayStart,
            Instant dayEnd,
            TransactionId excludeId
    );

    int countPayerCompletedOrInProgressSince(
            UserId payerId,
            Instant since,
            TransactionId excludeId
    );
}
