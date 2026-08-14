package dev.matheus.payment.domain.exception;

public final class TransferAmountLimitExceededException extends DomainException {

    public TransferAmountLimitExceededException(String message) {
        super(message);
    }
}
