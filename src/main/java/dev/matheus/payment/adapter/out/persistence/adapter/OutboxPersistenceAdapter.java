package dev.matheus.payment.adapter.out.persistence.adapter;

import dev.matheus.payment.adapter.out.persistence.entity.NotificationOutboxJpaEntity;
import dev.matheus.payment.adapter.out.persistence.entity.OutboxStatus;
import dev.matheus.payment.adapter.out.persistence.entity.TransferCompletedPayload;
import dev.matheus.payment.adapter.out.persistence.repository.NotificationOutboxJpaRepository;
import dev.matheus.payment.application.port.out.OutboxPort;
import dev.matheus.payment.domain.event.TransferCompleted;
import dev.matheus.payment.domain.model.UuidV7;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxPersistenceAdapter implements OutboxPort {

    static final String TRANSFER_COMPLETED_EVENT_TYPE = "TransferCompleted";

    private final NotificationOutboxJpaRepository jpaRepository;

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
}
