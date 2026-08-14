package dev.matheus.payment.adapter.in.messaging.rabbit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.matheus.payment.adapter.out.persistence.entity.NotificationOutboxJpaEntity;
import dev.matheus.payment.adapter.out.persistence.entity.OutboxStatus;
import dev.matheus.payment.adapter.out.persistence.entity.UserJpaEntity;
import dev.matheus.payment.adapter.out.persistence.repository.NotificationOutboxJpaRepository;
import dev.matheus.payment.adapter.out.persistence.repository.UserJpaRepository;
import dev.matheus.payment.application.port.out.OutboxPort;
import dev.matheus.payment.application.port.out.TransactionRepository;
import dev.matheus.payment.application.service.OutboxPublisherService;
import dev.matheus.payment.config.MessagingProperties;
import dev.matheus.payment.domain.enums.DocumentType;
import dev.matheus.payment.domain.enums.UserType;
import dev.matheus.payment.domain.event.TransferCompleted;
import dev.matheus.payment.domain.model.IdempotencyKey;
import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.Transaction;
import dev.matheus.payment.domain.model.UserId;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootTest
@Testcontainers
class NotificationFlowIntegrationTest {

    private static final AtomicLong DOCUMENTS = new AtomicLong(200_000_000_00L);

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
        registry.add("payment.notifier.base-url", wireMock::baseUrl);
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("payment.messaging.retry.max-attempts", () -> "1");
        registry.add("payment.messaging.retry.initial-interval", () -> "50ms");
        registry.add("payment.messaging.retry.multiplier", () -> "1");
        registry.add("payment.messaging.retry.max-interval", () -> "50ms");
    }

    @Autowired
    OutboxPort outboxPort;
    @Autowired
    OutboxPublisherService outboxPublisherService;
    @Autowired
    TransactionRepository transactionRepository;
    @Autowired
    UserJpaRepository userJpaRepository;
    @Autowired
    NotificationOutboxJpaRepository outboxJpaRepository;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    MessagingProperties messagingProperties;

    @BeforeEach
    void setUp() {
        amqpAdmin.purgeQueue(messagingProperties.queue(), true);
        amqpAdmin.purgeQueue(messagingProperties.dlq(), true);
        wireMock.resetAll();
    }

    @Test
    void publishesOutboxAndNotifiesPayee() {
        stubNotify(204);
        UUID eventId = savePendingOutbox();

        outboxPublisherService.publishPending();

        awaitUntil(Duration.ofSeconds(10), () -> wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/notify"))));
        NotificationOutboxJpaEntity row = outboxJpaRepository.findById(eventId).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, row.getStatus());
        assertNotNull(row.getPublishedAt());
    }

    @Test
    void exhaustedNotifyRetriesGoToDlq() {
        stubNotify(503);
        savePendingOutbox();

        outboxPublisherService.publishPending();

        Message dead = awaitMessage(messagingProperties.dlq());
        assertNotNull(dead);
        String body = new String(dead.getBody(), StandardCharsets.UTF_8);
        assertEquals(true, body.contains("TransferCompleted"));
    }

    @Test
    void malformedMessageGoesToDlqWithoutRequeueLoop() {
        Message malformed = new Message("<<<not-json>>>".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        malformed.getMessageProperties().setContentType("application/json");
        rabbitTemplate.send(messagingProperties.exchange(), messagingProperties.routingKey(), malformed);

        Message dead = awaitMessage(messagingProperties.dlq());
        assertNotNull(dead);
        assertEquals("<<<not-json>>>", new String(dead.getBody(), StandardCharsets.UTF_8));
        assertEquals(0, amqpAdmin.getQueueInfo(messagingProperties.queue()).getMessageCount());
    }

    private UUID savePendingOutbox() {
        UserId payer = persistUser(UserType.COMMON);
        UserId payee = persistUser(UserType.MERCHANT);
        Transaction transaction = Transaction.start(
                payer,
                payee,
                Money.ofTransfer(new BigDecimal("10.00")),
                IdempotencyKey.of("key-" + UUID.randomUUID())
        );
        transactionRepository.insertInProgress(transaction);
        transaction.complete();
        transactionRepository.update(transaction);
        TransferCompleted event = (TransferCompleted) transaction.pullEvents().getFirst();
        outboxPort.save(event);
        return outboxJpaRepository.findAll().stream()
                .filter(row -> row.getTransactionId().equals(transaction.id().value()))
                .findFirst()
                .orElseThrow()
                .getEventId();
    }

    private UserId persistUser(UserType type) {
        UserId id = UserId.generate();
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(id.value());
        entity.setFullName("Test User");
        entity.setDocumentType(DocumentType.CPF);
        entity.setDocumentValue(String.format("%011d", DOCUMENTS.incrementAndGet()));
        entity.setEmail(id.value() + "@example.com");
        entity.setPasswordHash("hash");
        entity.setType(type);
        entity.setCreatedAt(Instant.now());
        userJpaRepository.saveAndFlush(entity);
        return id;
    }

    private void stubNotify(int status) {
        wireMock.stubFor(post(urlEqualTo("/api/v1/notify"))
                .willReturn(aResponse().withStatus(status)));
    }

    private Message awaitMessage(String queue) {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            Message message = rabbitTemplate.receive(queue, 200);
            if (message != null) {
                return message;
            }
        }
        throw new AssertionError("no message received from " + queue);
    }

    private static void awaitUntil(Duration timeout, Runnable assertion) {
        Instant deadline = Instant.now().plus(timeout);
        AssertionError last = new AssertionError("timed out");
        while (Instant.now().isBefore(deadline)) {
            try {
                assertion.run();
                return;
            } catch (AssertionError | Exception ex) {
                last = ex instanceof AssertionError error ? error : new AssertionError(ex);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
        }
        throw last;
    }
}
