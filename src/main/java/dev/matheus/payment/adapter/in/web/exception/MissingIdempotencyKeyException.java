package dev.matheus.payment.adapter.in.web.exception;

public final class MissingIdempotencyKeyException extends RuntimeException {

    public MissingIdempotencyKeyException(String message) {
        super(message);
    }
}
