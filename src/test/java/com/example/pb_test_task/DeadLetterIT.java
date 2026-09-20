package com.example.pb_test_task;

import com.example.pb_test_task.config.RabbitConfig;
import com.example.pb_test_task.event.OrderRoutingKey;
import com.example.pb_test_task.processing.ProcessingDeadLetterListener;
import com.example.pb_test_task.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.pb_test_task.config.RabbitConfig.DEAD_LETTER_QUEUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.amqp.core.MessageProperties.CONTENT_TYPE_JSON;

class DeadLetterIT extends IntegrationTestBase {

    @Test
    void aPoisonMessageIsDeadLetteredInsteadOfRedeliveredForever() {
        withListenerPaused(ProcessingDeadLetterListener.LISTENER_ID, () -> {
            drainQueue(DEAD_LETTER_QUEUE);

            rabbitTemplate.send(RabbitConfig.EXCHANGE, OrderRoutingKey.CREATED, poisonMessage());

            AtomicReference<Message> received = new AtomicReference<>();
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                Message polled = rabbitTemplate.receive(DEAD_LETTER_QUEUE, 500);
                if (polled != null) {
                    received.set(polled);
                }
                assertThat(received.get()).isNotNull();
            });
            Message deadLettered = received.get();

            assertThat(new String(deadLettered.getBody(), UTF_8)).isEqualTo("{ this is not json");
            Object deathHeader = deadLettered.getMessageProperties().getHeader("x-death");
            assertThat(deathHeader).isNotNull();
            assertThat(rabbitTemplate.receive(DEAD_LETTER_QUEUE, 500)).isNull();
        });
    }

    private static Message poisonMessage() {
        return MessageBuilder.withBody("{ this is not json".getBytes(UTF_8))
                .setContentType(CONTENT_TYPE_JSON)
                .setContentEncoding(UTF_8.name())
                .build();
    }
}
