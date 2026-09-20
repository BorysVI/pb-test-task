package com.example.pb_test_task.support;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

public class ControllableRabbitTemplate extends RabbitTemplate {

    private final AtomicBoolean unavailable = new AtomicBoolean();

    public void simulateOutage(boolean outage) {
        unavailable.set(outage);
    }

    @Override
    public <T> T invoke(OperationsCallback<T> action,
                        com.rabbitmq.client.ConfirmCallback acks,
                        com.rabbitmq.client.ConfirmCallback nacks) {
        if (unavailable.get()) {
            throw new AmqpException("simulated broker outage");
        }
        return super.invoke(action, acks, nacks);
    }
}
