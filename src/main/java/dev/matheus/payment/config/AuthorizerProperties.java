package dev.matheus.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.authorizer")
public record AuthorizerProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
