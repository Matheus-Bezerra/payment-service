package dev.matheus.payment.adapter.out.persistence.adapter;

import dev.matheus.payment.adapter.out.persistence.entity.TransactionJpaEntity;
import dev.matheus.payment.adapter.out.persistence.mapper.TransactionPersistenceMapper;
import dev.matheus.payment.adapter.out.persistence.repository.TransactionJpaRepository;
import dev.matheus.payment.application.exception.DuplicateIdempotencyKeyException;
import dev.matheus.payment.application.port.out.TransactionRepository;
import dev.matheus.payment.domain.enums.TransactionStatus;
import dev.matheus.payment.domain.model.IdempotencyKey;
import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.Transaction;
import dev.matheus.payment.domain.model.TransactionId;
import dev.matheus.payment.domain.model.UserId;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TransactionPersistenceAdapter implements TransactionRepository {

    private static final Duration IN_PROGRESS_WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration IN_PROGRESS_POLL_INTERVAL = Duration.ofMillis(50);
    private static final Set<TransactionStatus> POLICY_STATUSES = Set.of(
            TransactionStatus.IN_PROGRESS,
            TransactionStatus.COMPLETED
    );

    private final TransactionJpaRepository jpaRepository;
    private final TransactionPersistenceMapper mapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertInProgress(Transaction transaction) {
        try {
            jpaRepository.saveAndFlush(mapper.toEntity(transaction));
        } catch (DataIntegrityViolationException ex) {
            if (isIdempotencyKeyViolation(ex)) {
                throw new DuplicateIdempotencyKeyException("duplicate idempotency key", ex);
            }
            throw ex;
        }
    }

    @Override
    public Transaction requireByIdempotencyKeyForUpdate(IdempotencyKey idempotencyKey) {
        Instant deadline = Instant.now().plus(IN_PROGRESS_WAIT_TIMEOUT);
        while (true) {
            Transaction transaction = jpaRepository.findByIdempotencyKeyForUpdate(idempotencyKey.value())
                    .map(mapper::toDomain)
                    .orElseThrow(() -> new IllegalStateException(
                            "transaction not found for idempotency key after unique violation"
                    ));
            if (transaction.status() != TransactionStatus.IN_PROGRESS
                    || Instant.now().isAfter(deadline)) {
                return transaction;
            }
            sleep();
        }
    }

    @Override
    public void update(Transaction transaction) {
        TransactionJpaEntity entity = jpaRepository.findById(transaction.id().value())
                .orElseThrow(() -> new IllegalStateException(
                        "transaction not found: " + transaction.id()
                ));
        entity.setStatus(transaction.status());
        entity.setFailureReason(transaction.failureReason());
        entity.setCompletedAt(transaction.completedAt());
    }

    @Override
    public Money sumPayerCompletedOrInProgressToday(
            UserId payerId,
            Instant dayStart,
            Instant dayEnd,
            TransactionId excludeId
    ) {
        return Money.ofBalance(Objects.requireNonNullElse(
                jpaRepository.sumPayerAmountByStatusBetween(
                        payerId.value(),
                        dayStart,
                        dayEnd,
                        excludeId.value(),
                        POLICY_STATUSES
                ),
                BigDecimal.ZERO
        ));
    }

    @Override
    public int countPayerCompletedOrInProgressSince(
            UserId payerId,
            Instant since,
            TransactionId excludeId
    ) {
        return jpaRepository.countByPayerIdAndIdNotAndStatusInAndCreatedAtAfter(
                payerId.value(),
                excludeId.value(),
                POLICY_STATUSES,
                since
        );
    }

    private static boolean isIdempotencyKeyViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraint
                    && constraint.getConstraintName() != null
                    && constraint.getConstraintName().contains("idempotency")) {
                return true;
            }
            if (current instanceof SQLException sql
                    && "23505".equals(sql.getSQLState())
                    && sql.getMessage() != null
                    && sql.getMessage().contains("idempotency")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void sleep() {
        try {
            Thread.sleep(IN_PROGRESS_POLL_INTERVAL);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for in-progress transfer", ex);
        }
    }
}
