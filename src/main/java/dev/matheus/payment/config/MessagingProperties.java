package dev.matheus.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.messaging")
public record MessagingProperties(
        String exchange,
        String routingKey,
        String queue,
        String dlq,
        String dlqRoutingKey,
        Duration outboxPollInterval,
        int outboxBatchSize,
        int outboxMaxAttempts,
        Duration publisherConfirmTimeout,
        Retry retry
) {

    public record Retry(
            int maxAttempts,
            Duration initialInterval,
            double multiplier,
            Duration maxInterval
    ) {
    }
}
