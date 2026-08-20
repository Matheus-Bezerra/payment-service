package dev.matheus.payment.adapter.in.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import dev.matheus.payment.adapter.out.persistence.entity.WalletJpaEntity;
import dev.matheus.payment.adapter.out.persistence.repository.NotificationOutboxJpaRepository;
import dev.matheus.payment.adapter.out.persistence.repository.TransactionJpaRepository;
import dev.matheus.payment.adapter.out.persistence.repository.WalletJpaRepository;
import dev.matheus.payment.application.port.out.ClockPort;
import dev.matheus.payment.domain.model.TransferPolicySnapshot;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "management.opentelemetry.tracing.export.otlp.enabled=false")
class TransferFlowIntegrationTest {

    private static final String JOAO = "0190a1b2-c3d4-7000-8000-000000000004";
    private static final String MATHEUS = "0190a1b2-c3d4-7000-8000-000000000006";
    private static final String LOJA = "0190a1b2-c3d4-7000-8000-000000000015";
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

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Container
    @ServiceConnection
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4-management");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("payment.authorizer.base-url", wireMock::baseUrl);
        registry.add("payment.seed.enabled", () -> "true");
        registry.add("spring.task.scheduling.enabled", () -> "false");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    WalletJpaRepository walletJpaRepository;

    @Autowired
    TransactionJpaRepository transactionJpaRepository;

    @Autowired
    NotificationOutboxJpaRepository outboxJpaRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    TransactionTemplate transactionTemplate;

    @MockitoBean
    ClockPort clockPort;

    @BeforeEach
    void setUp() {
        when(clockPort.now()).thenAnswer(invocation -> Instant.now());
        wireMock.resetAll();
        stubAuthorizerApproved();
        resetPersistentState();
    }

    @Test
    void happyPathReturns201AndUpdatesBalances() throws Exception {
        postTransfer(body("100.00", JOAO, LOJA), newKey())
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.value").value(100.00))
                .andExpect(jsonPath("$.payer").value(JOAO))
                .andExpect(jsonPath("$.payee").value(LOJA))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.completedAt").exists());

        assertBalance(JOAO, "49900.00");
        assertBalance(LOJA, "10100.00");
    }

    @Test
    void merchantPayerReturns403() throws Exception {
        postTransfer(body("100.00", LOJA, JOAO), newKey())
                .andExpect(problem("merchant-cannot-send-money", 403));

        wireMock.verify(0, getRequestedFor(urlEqualTo("/api/v2/authorize")));
        assertBalance(JOAO, "50000.00");
        assertBalance(LOJA, "10000.00");
    }

    @Test
    void insufficientBalanceReturns422() throws Exception {
        setBalance(JOAO, "50.00");

        postTransfer(body("100.00", JOAO, LOJA), newKey())
                .andExpect(problem("insufficient-balance", 422));

        assertBalance(JOAO, "50.00");
        assertBalance(LOJA, "10000.00");
    }

    @Test
    void authorizerDeniedReturns403() throws Exception {
        stubAuthorizerDenied();

        postTransfer(body("100.00", JOAO, LOJA), newKey())
                .andExpect(problem("transfer-not-authorized", 403));

        assertBalance(JOAO, "50000.00");
        assertBalance(LOJA, "10000.00");
    }

    @Test
    void completedReplayReturns200WithSameId() throws Exception {
        String key = newKey();
        String json = body("100.00", JOAO, LOJA);

        String firstBody = postTransfer(json, key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String transactionId = JsonPath.read(firstBody, "$.id");

        postTransfer(json, key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.value").value(100.00));

        wireMock.verify(1, getRequestedFor(urlEqualTo("/api/v2/authorize")));
        assertBalance(JOAO, "49900.00");
        assertBalance(LOJA, "10100.00");
    }

    @Test
    void failedReplayReturnsSameError() throws Exception {
        String key = newKey();
        String json = body("100.00", LOJA, JOAO);

        String firstDetail = postTransfer(json, key)
                .andExpect(problem("merchant-cannot-send-money", 403))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondDetail = postTransfer(json, key)
                .andExpect(problem("merchant-cannot-send-money", 403))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(firstDetail, secondDetail);
        wireMock.verify(0, getRequestedFor(urlEqualTo("/api/v2/authorize")));
        assertBalance(JOAO, "50000.00");
        assertBalance(LOJA, "10000.00");
    }

    @Test
    void pol01RejectsAboveDayCap() throws Exception {
        postTransfer(body("20000.01", JOAO, LOJA), newKey())
                .andExpect(problem("transfer-amount-limit-exceeded", 422));

        wireMock.verify(0, getRequestedFor(urlEqualTo("/api/v2/authorize")));
        assertBalance(JOAO, "50000.00");
    }

    @Test
    void pol02RejectsAboveNightCap() throws Exception {
        Instant night = LocalDate.now(TransferPolicySnapshot.ZONE)
                .atTime(23, 0)
                .atZone(TransferPolicySnapshot.ZONE)
                .toInstant();
        when(clockPort.now()).thenReturn(night);

        postTransfer(body("5000.01", JOAO, LOJA), newKey())
                .andExpect(problem("transfer-amount-limit-exceeded", 422));

        wireMock.verify(0, getRequestedFor(urlEqualTo("/api/v2/authorize")));
        assertBalance(JOAO, "50000.00");
    }

    @Test
    void pol03RejectsDailyLimit() throws Exception {
        for (int i = 0; i < 4; i++) {
            postTransfer(body("20000.00", MATHEUS, LOJA), newKey())
                    .andExpect(status().isCreated());
        }

        postTransfer(body("1000.01", MATHEUS, LOJA), newKey())
                .andExpect(problem("daily-transfer-limit-exceeded", 422));

        wireMock.verify(4, getRequestedFor(urlEqualTo("/api/v2/authorize")));
        assertBalance(MATHEUS, "20000.00");
    }

    @Test
    void pol04RejectsRateLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            postTransfer(body("1.00", JOAO, LOJA), newKey())
                    .andExpect(status().isCreated());
        }

        postTransfer(body("1.00", JOAO, LOJA), newKey())
                .andExpect(problem("transfer-rate-limit-exceeded", 422));

        wireMock.verify(5, getRequestedFor(urlEqualTo("/api/v2/authorize")));
        assertBalance(JOAO, "49995.00");
    }

    private ResultActions postTransfer(String json, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .content(json));
    }

    private static org.springframework.test.web.servlet.ResultMatcher problem(String slug, int status) {
        return result -> {
            status().is(status).match(result);
            content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.type").value("https://picpay-simplificado.local/problems/" + slug).match(result);
            jsonPath("$.status").value(status).match(result);
            jsonPath("$.instance").value("/transfer").match(result);
            jsonPath("$.title").exists().match(result);
        };
    }

    private static String body(String value, String payer, String payee) {
        return """
                {
                  "value": %s,
                  "payer": "%s",
                  "payee": "%s"
                }
                """.formatted(value, payer, payee);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private void stubAuthorizerApproved() {
        stubAuthorize(200, APPROVED);
    }

    private void stubAuthorizerDenied() {
        stubAuthorize(200, DENIED);
    }

    private static void stubAuthorize(int status, String body) {
        wireMock.stubFor(get(urlEqualTo("/api/v2/authorize"))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void resetPersistentState() {
        transactionTemplate.executeWithoutResult(status -> {
            outboxJpaRepository.deleteAll();
            transactionJpaRepository.deleteAll();
            setBalanceInCurrentTransaction(JOAO, "50000.00");
            setBalanceInCurrentTransaction(MATHEUS, "100000.00");
            setBalanceInCurrentTransaction(LOJA, "10000.00");
            entityManager.flush();
            entityManager.clear();
        });
    }

    private void setBalance(String userId, String amount) {
        transactionTemplate.executeWithoutResult(status -> {
            setBalanceInCurrentTransaction(userId, amount);
            entityManager.flush();
            entityManager.clear();
        });
    }

    private void setBalanceInCurrentTransaction(String userId, String amount) {
        WalletJpaEntity wallet = walletJpaRepository.findByUserId(UUID.fromString(userId)).orElseThrow();
        wallet.setBalance(new BigDecimal(amount));
        wallet.setUpdatedAt(Instant.now());
        walletJpaRepository.save(wallet);
    }

    private void assertBalance(String userId, String expected) {
        entityManager.clear();
        WalletJpaEntity wallet = walletJpaRepository.findByUserId(UUID.fromString(userId)).orElseThrow();
        assertEquals(0, new BigDecimal(expected).compareTo(wallet.getBalance()));
    }
}
