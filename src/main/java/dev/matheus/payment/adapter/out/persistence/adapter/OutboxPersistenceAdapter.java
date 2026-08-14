package dev.matheus.payment.adapter.out.persistence.adapter;

import dev.matheus.payment.adapter.out.persistence.entity.NotificationOutboxJpaEntity;
import dev.matheus.payment.adapter.out.persistence.entity.OutboxStatus;
import dev.matheus.payment.adapter.out.persistence.entity.TransferCompletedPayload;
import dev.matheus.payment.adapter.out.persistence.repository.NotificationOutboxJpaRepository;
import dev.matheus.payment.application.dto.PendingNotification;
import dev.matheus.payment.application.port.out.OutboxPort;
import dev.matheus.payment.domain.event.TransferCompleted;
import dev.matheus.payment.domain.model.UuidV7;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class OutboxPersistenceAdapter implements OutboxPort {

    static final String TRANSFER_COMPLETED_EVENT_TYPE = "TransferCompleted";

    private final NotificationOutboxJpaRepository jpaRepository;
    private final int outboxMaxAttempts;

    public OutboxPersistenceAdapter(
            NotificationOutboxJpaRepository jpaRepository,
            @Value("${payment.messaging.outbox-max-attempts:5}") int outboxMaxAttempts
    ) {
        this.jpaRepository = jpaRepository;
        this.outboxMaxAttempts = outboxMaxAttempts;
    }

    @Override
    public void save(TransferCompleted event) {
        NotificationOutboxJpaEntity entity = new NotificationOutboxJpaEntity();
        entity.setEventId(UuidV7.generate());
        entity.setTransactionId(event.transactionId().value());
        entity.setEventType(TRANSFER_COMPLETED_EVENT_TYPE);
        entity.setAggregateId(event.transactionId().value().toString());
        entity.setPayload(new TransferCompletedPayload(
                event.transactionId().value(),
                event.payerId().value(),
                event.payeeId().value(),
                event.amount().amount(),
                event.occurredAt()
        ));
        entity.setStatus(OutboxStatus.PENDING);
        entity.setAttempts(0);
        entity.setCreatedAt(Instant.now());
        jpaRepository.save(entity);
    }

    @Override
    public List<PendingNotification> claimPending(int batchSize) {
        return jpaRepository.findByStatusOrderByCreatedAtAsc(
                        OutboxStatus.PENDING,
                        PageRequest.of(0, batchSize)
                )
                .stream()
                .map(OutboxPersistenceAdapter::toPending)
                .toList();
    }

    @Override
    public void markPublished(UUID eventId) {
        NotificationOutboxJpaEntity entity = requireEvent(eventId);
        entity.setStatus(OutboxStatus.PUBLISHED);
        entity.setPublishedAt(Instant.now());
        jpaRepository.save(entity);
    }

    @Override
    public void markFailed(UUID eventId) {
        NotificationOutboxJpaEntity entity = requireEvent(eventId);
        entity.setAttempts(entity.getAttempts() + 1);
        if (entity.getAttempts() >= outboxMaxAttempts) {
            entity.setStatus(OutboxStatus.FAILED);
        }
        jpaRepository.save(entity);
    }

    private NotificationOutboxJpaEntity requireEvent(UUID eventId) {
        return jpaRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("outbox event not found: " + eventId));
    }

    private static PendingNotification toPending(NotificationOutboxJpaEntity entity) {
        return new PendingNotification(
                entity.getEventId(),
                entity.getTransactionId(),
                entity.getEventType(),
                entity.getAggregateId(),
                entity.getPayload(),
                entity.getAttempts()
        );
    }
}
