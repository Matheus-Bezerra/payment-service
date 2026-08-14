package dev.matheus.payment.adapter.out.persistence.repository;

import dev.matheus.payment.adapter.out.persistence.entity.NotificationOutboxJpaEntity;
import dev.matheus.payment.adapter.out.persistence.entity.OutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

public interface NotificationOutboxJpaRepository extends JpaRepository<NotificationOutboxJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<NotificationOutboxJpaEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
