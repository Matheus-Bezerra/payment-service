package dev.matheus.payment.domain.exception;

public final class SameAccountTransferException extends DomainException {

    public SameAccountTransferException(String message) {
        super(message);
    }
}
