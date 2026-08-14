package dev.matheus.payment.adapter.out.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.matheus.payment.application.exception.AuthorizationUnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

class AuthorizationClientAdapterTest {

    private static final String APPROVED = """
            {"status":"success","data":{"authorization":true}}
            """;
    private static final String DENIED = """
            {"status":"fail","data":{"authorization":false}}
            """;

    @RegisterExtension
    static final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private AuthorizationClientAdapter adapter;

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
        adapter = new AuthorizationClientAdapter(restClient);
    }

    @Test
    void approvedWhenAuthorizationTrue() {
        stubAuthorize(200, APPROVED);

        assertTrue(adapter.authorize());
    }

    @Test
    void deniedWhenAuthorizationFalseOn200() {
        stubAuthorize(200, DENIED);

        assertFalse(adapter.authorize());
    }

    @Test
    void deniedWhenAuthorizationFalseOn403() {
        stubAuthorize(403, DENIED);

        assertFalse(adapter.authorize());
    }

    @Test
    void unavailableOn503() {
        stubAuthorize(503, """
                {"status":"fail"}
                """);

        assertThrows(AuthorizationUnavailableException.class, adapter::authorize);
    }

    @Test
    void unavailableOnReadTimeout() {
        wireMock.stubFor(get(urlEqualTo("/api/v2/authorize"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(APPROVED)
                        .withFixedDelay(2_000)));

        assertThrows(AuthorizationUnavailableException.class, adapter::authorize);
    }

    @Test
    void unavailableOnInvalidJson() {
        stubAuthorize(200, "{not-json");

        assertThrows(AuthorizationUnavailableException.class, adapter::authorize);
    }

    private static void stubAuthorize(int status, String body) {
        wireMock.stubFor(get(urlEqualTo("/api/v2/authorize"))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
