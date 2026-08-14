package dev.matheus.payment.domain.exception;

public final class InsufficientBalanceException extends DomainException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
