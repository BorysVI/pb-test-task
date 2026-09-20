package com.example.pb_test_task;

import com.example.pb_test_task.config.RabbitConfig;
import com.example.pb_test_task.domain.OrderStatus;
import com.example.pb_test_task.domain.OutboxMessage;
import com.example.pb_test_task.event.OrderRoutingKey;
import com.example.pb_test_task.processing.StuckOrderReaper;
import com.example.pb_test_task.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class NewOrderRedriveIT extends IntegrationTestBase {

    private static final BigDecimal AMOUNT = new BigDecimal("2500.00");

    @Autowired
    private StuckOrderReaper reaper;

    @Test
    void anOrderWhoseEventWasLostIsRedrivenAndEventuallyCompletes() {
        UUID clientId = randomUUID();
        UUID orderId = createOrderWithLostEvent(clientId);

        strandInNewSince(orderId, beyondNewThreshold());
        reaper.redriveAbandoned();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(getOrder(orderId).require().status()).isEqualTo(OrderStatus.COMPLETED));

        assertThat(createdEventsFor(orderId)).hasSize(2);
        assertThat(reservedToday(clientId)).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void anOrderStrandedPastTheGiveUpWindowIsFailedAndItsLimitReturned() {
        UUID clientId = randomUUID();
        UUID orderId = createOrderWithLostEvent(clientId);

        strandInNewSince(orderId, beyondNewThreshold());
        backdateCreationTo(orderId, clock.instant().minus(properties.reaper().giveUpAfter()).minusSeconds(60));
        reaper.redriveAbandoned();

        var abandoned = getOrder(orderId).require();
        assertThat(abandoned.status()).isEqualTo(OrderStatus.FAILED);
        assertThat(abandoned.statusReason()).contains("Never picked up");
        assertThat(reservedToday(clientId)).isEqualByComparingTo("0.00");
        assertThat(createdEventsFor(orderId)).hasSize(1);
    }

    @Test
    void aRecentlyCreatedOrderIsNeitherRedrivenNorFailed() {
        UUID clientId = randomUUID();
        UUID orderId = createOrderWithLostEvent(clientId);

        reaper.redriveAbandoned();

        assertThat(getOrder(orderId).require().status()).isEqualTo(OrderStatus.NEW);
        assertThat(createdEventsFor(orderId)).hasSize(1);
        assertThat(reservedToday(clientId)).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void backToBackSweepsRedriveAnOrderOnlyOnce() {
        UUID clientId = randomUUID();

        withProcessingPaused(() -> {
            UUID orderId = createOrderWithLostEventWhilePaused(clientId);
            strandInNewSince(orderId, beyondNewThreshold());

            reaper.redriveAbandoned();
            reaper.redriveAbandoned();

            assertThat(createdEventsFor(orderId)).hasSize(2);
        });
    }

    private Instant beyondNewThreshold() {
        return clock.instant().minus(properties.reaper().newThreshold()).minusSeconds(60);
    }

    private UUID createOrderWithLostEvent(UUID clientId) {
        UUID[] holder = new UUID[1];
        withProcessingPaused(() -> holder[0] = createOrderWithLostEventWhilePaused(clientId));
        return holder[0];
    }

    private UUID createOrderWithLostEventWhilePaused(UUID clientId) {
        UUID orderId = createOrder(randomUUID(), clientId, AMOUNT).require().id();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outboxRepository.findByAggregateIdOrderByIdAsc(orderId))
                        .allSatisfy(message -> assertThat(message.getPublishedAt()).isNotNull()));
        drainQueue(RabbitConfig.PROCESSING_QUEUE);
        return orderId;
    }

    private void strandInNewSince(UUID orderId, Instant lastTouchedAt) {
        jdbcTemplate.update("UPDATE orders SET updated_at = ? WHERE id = ?",
                Timestamp.from(lastTouchedAt), orderId);
    }

    private void backdateCreationTo(UUID orderId, Instant createdAt) {
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt), orderId);
    }

    private List<OutboxMessage> createdEventsFor(UUID orderId) {
        return outboxRepository.findByAggregateIdOrderByIdAsc(orderId).stream()
                .filter(message -> OrderRoutingKey.CREATED.equals(message.getRoutingKey()))
                .toList();
    }
}
