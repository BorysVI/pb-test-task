package com.example.pb_test_task.processing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.example.pb_test_task.config.RabbitConfig.DEAD_LETTER_QUEUE;
import static java.nio.charset.StandardCharsets.UTF_8;

@Component
@Slf4j
public class ProcessingDeadLetterListener {

    public static final String LISTENER_ID = "processingDeadLetterListener";

    @RabbitListener(id = LISTENER_ID, queues = DEAD_LETTER_QUEUE)
    public void onDeadLetter(Message message) {
        log.error("Order event dead-lettered, needs manual reconciliation: messageId={} xDeath={} body={}",
                message.getMessageProperties().getMessageId(),
                message.getMessageProperties().getHeader("x-death"),
                new String(message.getBody(), UTF_8));
    }
}
