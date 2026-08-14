package dev.matheus.payment.application.result;

import dev.matheus.payment.domain.model.Transaction;
import java.util.Objects;

public record TransferResult(Transaction transaction, boolean replay) {

    public TransferResult {
        Objects.requireNonNull(transaction, "transaction is required");
    }
}
