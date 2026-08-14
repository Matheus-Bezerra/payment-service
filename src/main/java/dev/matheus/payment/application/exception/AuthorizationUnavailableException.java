package dev.matheus.payment.application.exception;

public final class AuthorizationUnavailableException extends RuntimeException {

    public AuthorizationUnavailableException(String message) {
        super(message);
    }

    public AuthorizationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
