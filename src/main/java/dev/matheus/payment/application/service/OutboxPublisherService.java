package dev.matheus.payment.application.service;

import dev.matheus.payment.application.dto.PendingNotification;
import dev.matheus.payment.application.port.out.NotificationPublisherPort;
import dev.matheus.payment.application.port.out.OutboxPort;
import dev.matheus.payment.config.MessagingProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);

    private final OutboxPort outboxPort;
    private final NotificationPublisherPort notificationPublisherPort;
    private final MessagingProperties messagingProperties;
    private final TransactionOperations transactionOperations;

    @Scheduled(fixedDelayString = "${payment.messaging.outbox-poll-interval:2s}")
    public void publishPending() {
        transactionOperations.executeWithoutResult(status -> {
            List<PendingNotification> pending = outboxPort.claimPending(messagingProperties.outboxBatchSize());
            for (PendingNotification notification : pending) {
                try {
                    notificationPublisherPort.publish(notification);
                    outboxPort.markPublished(notification.eventId());
                } catch (RuntimeException ex) {
                    log.warn("failed to publish outbox event {}", notification.eventId(), ex);
                    outboxPort.markFailed(notification.eventId());
                }
            }
        });
    }
}
