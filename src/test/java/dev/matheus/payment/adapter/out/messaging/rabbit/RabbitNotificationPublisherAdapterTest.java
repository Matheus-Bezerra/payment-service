package dev.matheus.payment.adapter.out.messaging.rabbit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.matheus.payment.adapter.out.persistence.entity.TransferCompletedPayload;
import dev.matheus.payment.application.dto.PendingNotification;
import dev.matheus.payment.config.MessagingProperties;
import dev.matheus.payment.config.RabbitConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@Testcontainers
class RabbitNotificationPublisherAdapterTest {

    @Container
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4-management");

    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate rabbitTemplate;
    private MessagingProperties properties;
    private RabbitNotificationPublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = messagingProperties("transfer.completed");
        connectionFactory = connectionFactory();
        RabbitConfig config = new RabbitConfig();
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.declareExchange(config.notificationsExchange(properties));
        admin.declareQueue(config.transferCompletedQueue(properties));
        admin.declareQueue(config.transferCompletedDlq(properties));
        admin.declareBinding(config.transferCompletedBinding(
                config.transferCompletedQueue(properties),
                config.notificationsExchange(properties),
                properties
        ));
        admin.declareBinding(config.transferCompletedDlqBinding(
                config.transferCompletedDlq(properties),
                config.notificationsExchange(properties),
                properties
        ));
        rabbitTemplate = config.rabbitTemplate(connectionFactory, config.jacksonJsonMessageConverter());
        adapter = new RabbitNotificationPublisherAdapter(rabbitTemplate, properties);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void publishesPersistentEnvelopeToConfiguredQueue() {
        PendingNotification notification = pendingNotification();

        adapter.publish(notification);

        Message message = rabbitTemplate.receive(properties.queue(), 5_000);
        assertNotNull(message);
        assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getReceivedDeliveryMode());
        assertEquals(notification.eventId().toString(), message.getMessageProperties().getMessageId());
        String body = new String(message.getBody());
        assertEquals(true, body.contains("\"eventType\":\"TransferCompleted\""));
        assertEquals(true, body.contains(notification.eventId().toString()));
    }

    @Test
    void throwsWhenMessageIsUnroutable() {
        RabbitNotificationPublisherAdapter unroutable = new RabbitNotificationPublisherAdapter(
                rabbitTemplate,
                messagingProperties("no.such.routing.key")
        );

        assertThrows(AmqpException.class, () -> unroutable.publish(pendingNotification()));
    }

    private CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory(rabbitmq.getHost(), rabbitmq.getAmqpPort());
        factory.setUsername(rabbitmq.getAdminUsername());
        factory.setPassword(rabbitmq.getAdminPassword());
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
    }

    private static MessagingProperties messagingProperties(String routingKey) {
        return new MessagingProperties(
                "payment.notifications",
                routingKey,
                "payment.notifications.transfer-completed",
                "payment.notifications.transfer-completed.dlq",
                "transfer.completed.dlq",
                Duration.ofSeconds(2),
                10,
                5,
                Duration.ofSeconds(5),
                new MessagingProperties.Retry(3, Duration.ofMillis(200), 2.0, Duration.ofSeconds(2))
        );
    }

    private static PendingNotification pendingNotification() {
        UUID eventId = UUID.fromString("0190a1b2-c3d4-7000-8000-000000000099");
        UUID transactionId = UUID.fromString("0190a1b2-c3d4-7000-8000-000000000042");
        return new PendingNotification(
                eventId,
                transactionId,
                "TransferCompleted",
                transactionId.toString(),
                new TransferCompletedPayload(
                        transactionId,
                        UUID.fromString("0190a1b2-c3d4-7000-8000-000000000004"),
                        UUID.fromString("0190a1b2-c3d4-7000-8000-000000000015"),
                        new BigDecimal("10.00"),
                        Instant.parse("2026-08-14T18:00:00Z")
                ),
                0
        );
    }
}
