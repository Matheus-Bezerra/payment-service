package dev.matheus.payment.adapter.out.client;

import dev.matheus.payment.adapter.out.persistence.entity.TransferCompletedPayload;
import dev.matheus.payment.application.exception.NotifierUnavailableException;
import dev.matheus.payment.application.port.out.NotifierPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class NotifierClientAdapter implements NotifierPort {

    static final String UNAVAILABLE_MESSAGE = "notification service unavailable";

    private final RestClient notifierRestClient;

    @Override
    public void notify(TransferCompletedPayload payload) {
        try {
            ResponseEntity<Void> entity = notifierRestClient.post()
                    .uri("/api/v1/notify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> { })
                    .toBodilessEntity();
            if (!entity.getStatusCode().is2xxSuccessful()) {
                throw new NotifierUnavailableException(UNAVAILABLE_MESSAGE);
            }
        } catch (NotifierUnavailableException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new NotifierUnavailableException(UNAVAILABLE_MESSAGE, ex);
        }
    }
}
