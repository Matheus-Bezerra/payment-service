package dev.matheus.payment.domain.exception;

public final class MerchantCannotSendMoneyException extends DomainException {

    public MerchantCannotSendMoneyException(String message) {
        super(message);
    }
}
