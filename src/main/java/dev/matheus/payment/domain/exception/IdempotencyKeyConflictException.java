package dev.matheus.payment.domain.exception;

public final class IdempotencyKeyConflictException extends DomainException {

    public IdempotencyKeyConflictException(String message) {
        super(message);
    }
}
