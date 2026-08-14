package dev.matheus.payment.adapter.out.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.matheus.payment.adapter.out.persistence.entity.TransferCompletedPayload;
import dev.matheus.payment.application.exception.NotifierUnavailableException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

class NotifierClientAdapterTest {

    @RegisterExtension
    static final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private NotifierClientAdapter adapter;

    @BeforeEach
    void setUp() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofMillis(500));
        RestClient restClient = RestClient.builder()
                .baseUrl(wireMock.baseUrl())
                .requestFactory(factory)
                .messageConverters(converters -> converters.add(new MappingJackson2HttpMessageConverter()))
                .build();
        adapter = new NotifierClientAdapter(restClient);
    }

    @Test
    void succeedsOn204() {
        stubNotify(204, "");

        assertDoesNotThrow(() -> adapter.notify(payload()));
    }

    @Test
    void unavailableOn503() {
        stubNotify(503, """
                {"status":"fail"}
                """);

        assertThrows(NotifierUnavailableException.class, () -> adapter.notify(payload()));
    }

    @Test
    void unavailableOnReadTimeout() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/notify"))
                .willReturn(aResponse()
                        .withStatus(204)
                        .withFixedDelay(2_000)));

        assertThrows(NotifierUnavailableException.class, () -> adapter.notify(payload()));
    }

    private static void stubNotify(int status, String body) {
        wireMock.stubFor(post(urlEqualTo("/api/v1/notify"))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static TransferCompletedPayload payload() {
        return new TransferCompletedPayload(
                UUID.fromString("0190a1b2-c3d4-7000-8000-000000000042"),
                UUID.fromString("0190a1b2-c3d4-7000-8000-000000000004"),
                UUID.fromString("0190a1b2-c3d4-7000-8000-000000000015"),
                new BigDecimal("10.00"),
                Instant.parse("2026-08-14T18:00:00Z")
        );
    }
}
