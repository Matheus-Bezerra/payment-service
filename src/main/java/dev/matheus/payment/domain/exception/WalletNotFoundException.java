package dev.matheus.payment.domain.exception;

public final class WalletNotFoundException extends DomainException {

    public WalletNotFoundException(String message) {
        super(message);
    }
}
