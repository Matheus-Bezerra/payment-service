package dev.matheus.payment.application.dto;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        String aggregateId,
        Instant occurredAt,
        String correlationId,
        T payload
) {
}
