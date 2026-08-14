package dev.matheus.payment.adapter.out.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferCompletedPayload(
        UUID transactionId,
        UUID payerId,
        UUID payeeId,
        BigDecimal amount,
        Instant occurredAt
) {
}
