package dev.matheus.payment.application.exception;

public final class NotifierUnavailableException extends RuntimeException {

    public NotifierUnavailableException(String message) {
        super(message);
    }

    public NotifierUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
