package dev.matheus.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.notifier")
public record NotifierProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
