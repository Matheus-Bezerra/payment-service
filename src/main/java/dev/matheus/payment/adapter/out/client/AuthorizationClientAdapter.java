package dev.matheus.payment.adapter.out.client;

import dev.matheus.payment.adapter.out.client.dto.AuthorizationResponse;
import dev.matheus.payment.application.exception.AuthorizationUnavailableException;
import dev.matheus.payment.application.port.out.AuthorizationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class AuthorizationClientAdapter implements AuthorizationPort {

    static final String UNAVAILABLE_MESSAGE = "authorization service unavailable";

    private final RestClient authorizerRestClient;

    @Override
    public boolean authorize() {
        try {
            ResponseEntity<AuthorizationResponse> entity = authorizerRestClient.get()
                    .uri("/api/v2/authorize")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> { })
                    .toEntity(AuthorizationResponse.class);
            if (unavailable(entity.getStatusCode())) {
                throw new AuthorizationUnavailableException(UNAVAILABLE_MESSAGE);
            }
            AuthorizationResponse body = entity.getBody();
            return body != null && body.authorized();
        } catch (AuthorizationUnavailableException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new AuthorizationUnavailableException(UNAVAILABLE_MESSAGE, ex);
        }
    }

    private static boolean unavailable(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == 429;
    }
}
