package dev.matheus.payment.domain.exception;

public final class UserNotFoundException extends DomainException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
