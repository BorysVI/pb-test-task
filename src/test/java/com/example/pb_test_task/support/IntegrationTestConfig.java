package com.example.pb_test_task.support;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfig {

    @Bean
    @Primary
    ScriptedPaymentProvider scriptedPaymentProvider() {
        return new ScriptedPaymentProvider();
    }

    @Bean
    ControllableRabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        ControllableRabbitTemplate template = new ControllableRabbitTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
