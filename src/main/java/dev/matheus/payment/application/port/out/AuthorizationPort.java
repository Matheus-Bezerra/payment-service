package dev.matheus.payment.application.port.out;

import dev.matheus.payment.application.exception.AuthorizationUnavailableException;

public interface AuthorizationPort {

    /**
     * Consults the external authorizer.
     *
     * @return {@code true} when approved, {@code false} when denied
     * @throws AuthorizationUnavailableException when the authorizer times out, is unreachable, or returns 5xx
     */
    boolean authorize();
}
