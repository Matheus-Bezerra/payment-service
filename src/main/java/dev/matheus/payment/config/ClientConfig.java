package dev.matheus.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({AuthorizerProperties.class, NotifierProperties.class})
public class ClientConfig {

    @Bean
    RestClient authorizerRestClient(AuthorizerProperties properties) {
        return restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    RestClient notifierRestClient(NotifierProperties properties) {
        return restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
    }

    private static RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(orDefault(connectTimeout));
        factory.setReadTimeout(orDefault(readTimeout));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    private static Duration orDefault(Duration timeout) {
        return timeout == null ? Duration.ofSeconds(2) : timeout;
    }
}
