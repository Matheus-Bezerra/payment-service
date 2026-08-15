package dev.matheus.payment.adapter.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Transfer request. payer/payee are seed user IDs (not wallet IDs).")
public record TransferRequest(
        @Schema(description = "Amount in BRL", example = "100.00")
        @NotNull @Positive BigDecimal value,
        @Schema(
                description = "COMMON user who sends. Seed: João 0190a1b2-c3d4-7000-8000-000000000004 "
                        + "or Matheus 0190a1b2-c3d4-7000-8000-000000000006",
                example = "0190a1b2-c3d4-7000-8000-000000000004"
        )
        @NotNull UUID payer,
        @Schema(
                description = "User who receives. Seed: Loja 0190a1b2-c3d4-7000-8000-000000000015 "
                        + "or Mercado 0190a1b2-c3d4-7000-8000-000000000016",
                example = "0190a1b2-c3d4-7000-8000-000000000015"
        )
        @NotNull UUID payee
) {
}
