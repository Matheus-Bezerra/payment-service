package dev.matheus.payment.domain.exception;

public final class TransferRateLimitExceededException extends DomainException {

    public TransferRateLimitExceededException(String message) {
        super(message);
    }
}
