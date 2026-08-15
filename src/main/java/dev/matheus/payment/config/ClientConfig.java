package dev.matheus.payment.config;

import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
        AuthorizerProperties.class,
        NotifierProperties.class,
        HttpClientProperties.class
})
public class ClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ClientConfig.class);

    @Bean
    RestClient authorizerRestClient(AuthorizerProperties properties, HttpClientProperties http) {
        return restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout(), http);
    }

    @Bean
    RestClient notifierRestClient(NotifierProperties properties, HttpClientProperties http) {
        return restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout(), http);
    }

    private static RestClient restClient(
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout,
            HttpClientProperties http
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(connectTimeout, readTimeout, http.sslVerify()))
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(
            Duration connectTimeout,
            Duration readTimeout,
            boolean sslVerify
    ) {
        Duration connect = orDefault(connectTimeout);
        Duration read = orDefault(readTimeout);
        if (sslVerify) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connect);
            factory.setReadTimeout(read);
            return factory;
        }
        log.warn("TLS certificate verification disabled for outbound HTTP clients (local mock only)");
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connect)
                .sslContext(trustAllSslContext())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(read);
        return factory;
    }

    private static SSLContext trustAllSslContext() {
        try {
            TrustManager[] trustAll = {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("failed to create insecure SSL context", ex);
        }
    }

    private static Duration orDefault(Duration timeout) {
        return timeout == null ? Duration.ofSeconds(2) : timeout;
    }
}
