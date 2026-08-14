package dev.matheus.payment.application.dto;

import dev.matheus.payment.adapter.out.persistence.entity.TransferCompletedPayload;
import java.util.UUID;

public record PendingNotification(
        UUID eventId,
        UUID transactionId,
        String eventType,
        String aggregateId,
        TransferCompletedPayload payload,
        int attempts
) {
}
