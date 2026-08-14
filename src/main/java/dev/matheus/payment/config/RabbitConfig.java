package dev.matheus.payment.config;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitConfig {

    static final String[] TRUSTED_PACKAGES = {
            "dev.matheus.payment.application.dto",
            "dev.matheus.payment.adapter.out.persistence.entity",
            "java.util",
            "java.math",
            "java.time"
    };

    @Bean
    public TopicExchange notificationsExchange(MessagingProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    @Bean
    public Queue transferCompletedQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.queue())
                .deadLetterExchange(properties.exchange())
                .deadLetterRoutingKey(properties.dlqRoutingKey())
                .build();
    }

    @Bean
    public Queue transferCompletedDlq(MessagingProperties properties) {
        return QueueBuilder.durable(properties.dlq()).build();
    }

    @Bean
    public Binding transferCompletedBinding(Queue transferCompletedQueue, TopicExchange notificationsExchange,
            MessagingProperties properties) {
        return BindingBuilder.bind(transferCompletedQueue)
                .to(notificationsExchange)
                .with(properties.routingKey());
    }

    @Bean
    public Binding transferCompletedDlqBinding(Queue transferCompletedDlq, TopicExchange notificationsExchange,
            MessagingProperties properties) {
        return BindingBuilder.bind(transferCompletedDlq)
                .to(notificationsExchange)
                .with(properties.dlqRoutingKey());
    }

    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(TRUSTED_PACKAGES);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter jacksonJsonMessageConverter
    ) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonJsonMessageConverter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter jacksonJsonMessageConverter,
            RabbitTemplate rabbitTemplate,
            MessagingProperties properties
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonJsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryAdvice(rabbitTemplate, properties));
        return factory;
    }

    private static Advice retryAdvice(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
        MessagingProperties.Retry retry = properties.retry();
        RepublishMessageRecoverer recoverer = new RepublishMessageRecoverer(
                rabbitTemplate,
                properties.exchange(),
                properties.dlqRoutingKey()
        );
        return RetryInterceptorBuilder.stateless()
                .maxRetries(retry.maxAttempts())
                .backOffOptions(
                        retry.initialInterval().toMillis(),
                        retry.multiplier(),
                        retry.maxInterval().toMillis()
                )
                .recoverer(recoverer)
                .build();
    }
}
