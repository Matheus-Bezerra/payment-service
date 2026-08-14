package dev.matheus.payment.domain.exception;

public final class InvalidDocumentException extends DomainException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}
