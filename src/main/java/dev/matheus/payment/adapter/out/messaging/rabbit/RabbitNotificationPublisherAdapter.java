package dev.matheus.payment.adapter.out.messaging.rabbit;

import dev.matheus.payment.application.dto.EventEnvelope;
import dev.matheus.payment.application.dto.PendingNotification;
import dev.matheus.payment.application.port.out.NotificationPublisherPort;
import dev.matheus.payment.config.MessagingProperties;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RabbitNotificationPublisherAdapter implements NotificationPublisherPort {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    @Override
    public void publish(PendingNotification notification) {
        EventEnvelope<?> envelope = new EventEnvelope<>(
                notification.eventId(),
                notification.eventType(),
                notification.aggregateId(),
                notification.payload().occurredAt(),
                notification.eventId().toString(),
                notification.payload()
        );
        CorrelationData correlation = new CorrelationData(notification.eventId().toString());
        rabbitTemplate.convertAndSend(
                properties.exchange(),
                properties.routingKey(),
                envelope,
                message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setMessageId(notification.eventId().toString());
                    message.getMessageProperties().setCorrelationId(notification.eventId().toString());
                    return message;
                },
                correlation
        );
        confirmOrThrow(notification, correlation);
    }

    private void confirmOrThrow(PendingNotification notification, CorrelationData correlation) {
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    properties.publisherConfirmTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
            if (confirm == null || !confirm.ack()) {
                throw new AmqpException("broker nacked notification " + notification.eventId()
                        + (confirm == null || confirm.reason() == null ? "" : ": " + confirm.reason()));
            }
            if (correlation.getReturned() != null) {
                throw new AmqpException("broker returned unroutable notification " + notification.eventId());
            }
        } catch (AmqpException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AmqpException("interrupted waiting for broker confirm for " + notification.eventId(), ex);
        } catch (Exception ex) {
            throw new AmqpException("failed waiting for broker confirm for " + notification.eventId(), ex);
        }
    }
}
