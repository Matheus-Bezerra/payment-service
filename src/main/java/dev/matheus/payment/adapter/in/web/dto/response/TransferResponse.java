package dev.matheus.payment.adapter.in.web.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        BigDecimal value,
        UUID payer,
        UUID payee,
        String status,
        Instant createdAt,
        Instant completedAt
) {
}
