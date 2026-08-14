package dev.matheus.payment.domain.exception;

public final class InvalidMoneyException extends DomainException {

    public InvalidMoneyException(String message) {
        super(message);
    }
}
