package com.example.pb_test_task;

import com.example.pb_test_task.domain.OrderStatus;
import com.example.pb_test_task.domain.OutboxMessage;
import com.example.pb_test_task.event.OrderRoutingKey;
import com.example.pb_test_task.processing.StuckOrderReaper;
import com.example.pb_test_task.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

class StuckOrderReaperIT extends IntegrationTestBase {

    private static final BigDecimal AMOUNT = new BigDecimal("4000.00");

    @Autowired
    private StuckOrderReaper reaper;

    @Test
    void anOrderAbandonedInProcessingIsFailedAndItsReservationReturned() {
        UUID clientId = randomUUID();
        UUID orderId = createAbandonedProcessingOrder(clientId, beyondTheReaperThreshold());

        assertThat(reservedToday(clientId)).isEqualByComparingTo(AMOUNT);

        reaper.reap();

        var reaped = getOrder(orderId).require();
        assertThat(reaped.status()).isEqualTo(OrderStatus.FAILED);
        assertThat(reaped.statusReason()).contains("reaper");
        assertThat(reaped.history())
                .extracting(item -> item.toStatus().name())
                .containsExactly("NEW", "PROCESSING", "FAILED");
        assertThat(reservedToday(clientId)).isEqualByComparingTo("0.00");
        assertThat(outboxRepository.findByAggregateIdOrderByIdAsc(orderId))
                .extracting(OutboxMessage::getRoutingKey)
                .endsWith(OrderRoutingKey.FAILED);
    }

    @Test
    void anOrderStillWithinTheThresholdIsLeftAlone() {
        UUID clientId = randomUUID();
        UUID orderId = createAbandonedProcessingOrder(clientId, clock.instant());

        reaper.reap();

        assertThat(getOrder(orderId).require().status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(reservedToday(clientId)).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void reapingIsIdempotentAndReleasesTheLimitOnlyOnce() {
        UUID clientId = randomUUID();
        createAbandonedProcessingOrder(clientId, beyondTheReaperThreshold());

        reaper.reap();
        reaper.reap();

        assertThat(reservedToday(clientId)).isEqualByComparingTo("0.00");
    }

    private Instant beyondTheReaperThreshold() {
        return clock.instant().minus(properties.reaper().threshold()).minusSeconds(60);
    }

    private UUID createAbandonedProcessingOrder(UUID clientId, Instant lastTouchedAt) {
        UUID orderId = withProcessingPausedReturning(() ->
                createOrder(randomUUID(), clientId, AMOUNT).require().id());
        jdbcTemplate.update("""
                INSERT INTO order_status_history (order_id, from_status, to_status, created_at)
                VALUES (?, 'NEW', 'PROCESSING', ?)
                """, orderId, java.sql.Timestamp.from(lastTouchedAt));
        jdbcTemplate.update("UPDATE orders SET status = 'PROCESSING', updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(lastTouchedAt), orderId);
        return orderId;
    }

    private UUID withProcessingPausedReturning(Supplier<UUID> action) {
        UUID[] holder = new UUID[1];
        withProcessingPaused(() -> holder[0] = action.get());
        return holder[0];
    }
}
