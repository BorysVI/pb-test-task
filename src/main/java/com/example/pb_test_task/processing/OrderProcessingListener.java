package com.example.pb_test_task.processing;

import com.example.pb_test_task.event.OrderEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.example.pb_test_task.config.RabbitConfig.PROCESSING_QUEUE;

@Component
public class OrderProcessingListener {

    public static final String LISTENER_ID = "orderProcessingListener";

    private final OrderProcessor processor;

    public OrderProcessingListener(OrderProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(id = LISTENER_ID, queues = PROCESSING_QUEUE)
    public void onOrderCreated(OrderEvent event) {
        processor.process(event.orderId());
    }
}
