package dev.matheus.payment.application.port.out;

public interface AuthorizationPort {

    /**
     * @return {@code true} when the external authorizer approves the transfer
     */
    boolean authorize();
}
