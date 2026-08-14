package dev.matheus.payment.domain.exception;

public final class DailyTransferLimitExceededException extends DomainException {

    public DailyTransferLimitExceededException(String message) {
        super(message);
    }
}
