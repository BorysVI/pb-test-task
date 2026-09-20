package com.example.pb_test_task;

import com.example.pb_test_task.config.RabbitConfig;
import com.example.pb_test_task.domain.Order;
import com.example.pb_test_task.domain.OrderStatus;
import com.example.pb_test_task.domain.OutboxMessage;
import com.example.pb_test_task.event.OrderRoutingKey;
import com.example.pb_test_task.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OutboxConsistencyIT extends IntegrationTestBase {

    private static final BigDecimal AMOUNT = new BigDecimal("1100.00");

    @AfterEach
    void restoreBroker() {
        rabbitTemplate.simulateOutage(false);
    }

    @Test
    void everyCommittedStatusChangeIsDeliveredToTheBroker() {
        UUID clientId = randomUUID();
        UUID orderId = createOrder(randomUUID(), clientId, AMOUNT).require().id();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(getOrder(orderId).require().status()).isEqualTo(OrderStatus.COMPLETED));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<OutboxMessage> messages = outboxRepository.findByAggregateIdOrderByIdAsc(orderId);
            assertThat(messages).extracting(OutboxMessage::getRoutingKey)
                    .containsExactly(OrderRoutingKey.CREATED, OrderRoutingKey.PROCESSING_STARTED, OrderRoutingKey.COMPLETED);
            assertThat(messages).allSatisfy(message -> assertThat(message.getPublishedAt()).isNotNull());
        });

        assertThat(drainEventsQueueFor(orderId)).isNotEmpty();
    }

    @Test
    void aRolledBackTransactionWritesNeitherAnOrderNorAnEvent() {
        UUID clientId = randomUUID();

        withProcessingPaused(() -> {
            UUID acceptedId = createOrder(randomUUID(), clientId, properties.dailyLimit()).require().id();

            var rejected = createOrder(randomUUID(), clientId, new BigDecimal("1000.00"));

            assertThat(rejected.status().value()).isEqualTo(409);
            assertThat(orderRepository.findAll())
                    .filteredOn(order -> order.getClientId().equals(clientId))
                    .extracting(Order::getId)
                    .containsExactly(acceptedId);
            assertThat(outboxRepository.findByAggregateIdOrderByIdAsc(acceptedId))
                    .extracting(OutboxMessage::getRoutingKey)
                    .containsExactly(OrderRoutingKey.CREATED);
            assertThat(reservedToday(clientId)).isEqualByComparingTo(properties.dailyLimit());
        });
    }

    @Test
    void aBrokerOutageDelaysPublicationButNeverLosesTheEvent() {
        rabbitTemplate.simulateOutage(true);

        UUID clientId = randomUUID();
        UUID orderId = createOrder(randomUUID(), clientId, AMOUNT).require().id();

        await().atMost(Duration.ofSeconds(5)).during(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(outboxRepository.findByAggregateIdOrderByIdAsc(orderId))
                        .singleElement()
                        .satisfies(message -> assertThat(message.getPublishedAt()).isNull()));
        assertThat(getOrder(orderId).require().status()).isEqualTo(OrderStatus.NEW);

        rabbitTemplate.simulateOutage(false);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(getOrder(orderId).require().status()).isEqualTo(OrderStatus.COMPLETED));
    }

    private List<String> drainEventsQueueFor(UUID orderId) {
        List<String> matching = new ArrayList<>();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Message message = rabbitTemplate.receive(RabbitConfig.EVENTS_QUEUE, 200);
            while (message != null) {
                String payload = new String(message.getBody(), StandardCharsets.UTF_8);
                if (payload.contains(orderId.toString())) {
                    matching.add(payload);
                }
                message = rabbitTemplate.receive(RabbitConfig.EVENTS_QUEUE, 200);
            }
            assertThat(matching).isNotEmpty();
        });
        return matching;
    }
}
