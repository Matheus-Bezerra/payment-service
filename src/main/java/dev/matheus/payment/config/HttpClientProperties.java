package dev.matheus.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.http")
public record HttpClientProperties(boolean sslVerify) {
}
