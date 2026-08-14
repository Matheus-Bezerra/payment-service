package dev.matheus.payment.domain.exception;

public final class InvalidTransactionStateException extends DomainException {

    public InvalidTransactionStateException(String message) {
        super(message);
    }
}
