package dev.matheus.payment.domain.exception;

public final class InvalidTransferAmountException extends DomainException {

    public InvalidTransferAmountException(String message) {
        super(message);
    }
}
