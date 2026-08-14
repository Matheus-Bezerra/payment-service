package dev.matheus.payment.domain.exception;

public final class TransferNotAuthorizedException extends DomainException {

    public TransferNotAuthorizedException(String message) {
        super(message);
    }
}
