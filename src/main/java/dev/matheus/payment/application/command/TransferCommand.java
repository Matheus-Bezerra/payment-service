package dev.matheus.payment.application.command;

import dev.matheus.payment.domain.model.IdempotencyKey;
import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.UserId;
import java.util.Objects;

public record TransferCommand(
        UserId payerId,
        UserId payeeId,
        Money amount,
        IdempotencyKey idempotencyKey
) {
    public TransferCommand {
        Objects.requireNonNull(payerId, "payerId is required");
        Objects.requireNonNull(payeeId, "payeeId is required");
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
    }
}
