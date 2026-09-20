package com.example.pb_test_task.outbox;

import com.example.pb_test_task.config.OrderProperties;
import com.example.pb_test_task.domain.OutboxMessage;
import com.example.pb_test_task.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

import static com.example.pb_test_task.config.RabbitConfig.EXCHANGE;
import static java.lang.Boolean.TRUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT;
import static org.springframework.amqp.core.MessageProperties.CONTENT_TYPE_JSON;

@RequiredArgsConstructor
@Component
public class OutboxPublisher {

    private final OutboxMessageRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;
    private final OrderProperties properties;

    @Scheduled(fixedDelayString = "${orders.outbox.poll-interval}")
    @Transactional
    public void publishPending() {
        List<OutboxMessage> batch = repository.lockUnpublishedBatch(properties.outbox().batchSize());
        if (batch.isEmpty()) {
            return;
        }
        boolean confirmed = TRUE.equals(rabbitTemplate.invoke(operations -> {
            batch.forEach(message -> operations.send(EXCHANGE, message.getRoutingKey(), toAmqp(message)));
            return operations.waitForConfirms(properties.outbox().confirmTimeout().toMillis());
        }));
        if (!confirmed) {
            throw new IllegalStateException("Broker did not confirm outbox batch of " + batch.size() + " message(s)");
        }
        repository.markPublished(batch.stream().map(OutboxMessage::getId).toList(), clock.instant());
    }

    private static Message toAmqp(OutboxMessage message) {
        return MessageBuilder.withBody(message.getPayload().getBytes(UTF_8))
                .setContentType(CONTENT_TYPE_JSON)
                .setContentEncoding(UTF_8.name())
                .setMessageId(String.valueOf(message.getId()))
                .setHeader("eventType", message.getEventType())
                .setDeliveryMode(PERSISTENT)
                .build();
    }
}
