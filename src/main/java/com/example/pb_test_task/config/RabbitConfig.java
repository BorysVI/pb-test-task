package com.example.pb_test_task.config;

import com.example.pb_test_task.event.OrderRoutingKey;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class RabbitConfig {

    public static final String EXCHANGE = "orders.events";
    public static final String DEAD_LETTER_EXCHANGE = "orders.dlx";
    public static final String PROCESSING_QUEUE = "q.orders.processing";
    public static final String EVENTS_QUEUE = "q.orders.events";
    public static final String DEAD_LETTER_QUEUE = "q.orders.processing.dlq";

    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper, "com.example.pb_test_task.*");
    }

    @Bean
    TopicExchange ordersExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    TopicExchange ordersDeadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue processingQueue() {
        return QueueBuilder.durable(PROCESSING_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(OrderRoutingKey.CREATED)
                .build();
    }

    @Bean
    Queue eventsQueue() {
        return QueueBuilder.durable(EVENTS_QUEUE).build();
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding processingBinding(Queue processingQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(processingQueue).to(ordersExchange).with(OrderRoutingKey.CREATED);
    }

    @Bean
    Binding eventsBinding(Queue eventsQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(eventsQueue).to(ordersExchange).with(OrderRoutingKey.ANY);
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange ordersDeadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(ordersDeadLetterExchange).with("#");
    }
}
