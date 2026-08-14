package dev.matheus.payment.adapter.out.persistence.repository;

import dev.matheus.payment.adapter.out.persistence.entity.TransactionJpaEntity;
import dev.matheus.payment.domain.enums.TransactionStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional
    @Query("SELECT t FROM TransactionJpaEntity t WHERE t.idempotencyKey = :key")
    Optional<TransactionJpaEntity> findByIdempotencyKeyForUpdate(@Param("key") String key);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
              FROM TransactionJpaEntity t
             WHERE t.payerId = :payerId
               AND t.id <> :excludeId
               AND t.status IN :statuses
               AND t.createdAt >= :dayStart
               AND t.createdAt < :dayEnd
            """)
    BigDecimal sumPayerAmountByStatusBetween(
            @Param("payerId") UUID payerId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd,
            @Param("excludeId") UUID excludeId,
            @Param("statuses") Collection<TransactionStatus> statuses
    );

    int countByPayerIdAndIdNotAndStatusInAndCreatedAtAfter(
            UUID payerId,
            UUID excludeId,
            Collection<TransactionStatus> statuses,
            Instant since
    );
}
