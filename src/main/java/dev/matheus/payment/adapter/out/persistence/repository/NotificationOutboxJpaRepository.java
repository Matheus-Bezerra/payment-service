package dev.matheus.payment.adapter.out.persistence.repository;

import dev.matheus.payment.adapter.out.persistence.entity.NotificationOutboxJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationOutboxJpaRepository extends JpaRepository<NotificationOutboxJpaEntity, UUID> {
}
