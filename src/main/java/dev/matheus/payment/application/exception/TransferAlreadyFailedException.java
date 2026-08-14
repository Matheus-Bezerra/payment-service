package dev.matheus.payment.application.exception;

public final class TransferAlreadyFailedException extends RuntimeException {

    public TransferAlreadyFailedException(String message) {
        super(message);
    }
}
