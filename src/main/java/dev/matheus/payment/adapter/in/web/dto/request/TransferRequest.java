package dev.matheus.payment.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull @Positive BigDecimal value,
        @NotNull UUID payer,
        @NotNull UUID payee
) {
}
