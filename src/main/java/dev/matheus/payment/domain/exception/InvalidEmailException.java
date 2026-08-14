package dev.matheus.payment.domain.exception;

public final class InvalidEmailException extends DomainException {

    public InvalidEmailException(String message) {
        super(message);
    }
}
