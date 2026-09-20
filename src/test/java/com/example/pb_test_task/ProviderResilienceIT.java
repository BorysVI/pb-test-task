package com.example.pb_test_task;

import com.example.pb_test_task.domain.OrderStatus;
import com.example.pb_test_task.domain.OutboxMessage;
import com.example.pb_test_task.event.OrderRoutingKey;
import com.example.pb_test_task.support.IntegrationTestBase;
import com.example.pb_test_task.support.ScriptedPaymentProvider.Outcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ProviderResilienceIT extends IntegrationTestBase {

    private static final BigDecimal AMOUNT = new BigDecimal("1200.00");

    @Test
    void transientServerErrorsAreRetriedUntilTheProviderSucceeds() {
        UUID clientId = randomUUID();
        provider.script(clientId, Outcome.SERVER_ERROR, Outcome.SERVER_ERROR, Outcome.SUCCESS);

        UUID orderId = createOrder(randomUUID(), clientId, AMOUNT).require().id();

        awaitStatus(orderId, OrderStatus.COMPLETED);
        assertThat(provider.callCount(clientId)).isEqualTo(3);
        assertThat(reservedToday(clientId)).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void retriesAreCappedAtThreeCallsAndTheLimitIsReleasedOnFailure() {
        UUID clientId = randomUUID();
        provider.script(clientId, Outcome.SERVER_ERROR, Outcome.SERVER_ERROR, Outcome.SERVER_ERROR,
                Outcome.SERVER_ERROR, Outcome.SERVER_ERROR);

        UUID orderId = createOrder(randomUUID(), clientId, AMOUNT).require().id();

        awaitStatus(orderId, OrderStatus.FAILED);
        assertThat(provider.callCount(clientId)).isEqualTo(3);
        assertThat(reservedToday(clientId)).isEqualByComparingTo("0.00");
        assertThat(outboxRepository.findByAggregateIdOrderByIdAsc(orderId))
                .extracting(OutboxMessage::getRoutingKey)
                .containsExactly(OrderRoutingKey.CREATED, OrderRoutingKey.PROCESSING_STARTED, OrderRoutingKey.FAILED);
    }

    @Test
    void aHangingProviderFailsFastWithoutRetrying() {
        UUID clientId = randomUUID();
        provider.script(clientId, Outcome.HANG, Outcome.HANG, Outcome.HANG);

        long startedAt = System.nanoTime();
        UUID orderId = createOrder(randomUUID(), clientId, AMOUNT).require().id();
        awaitStatus(orderId, OrderStatus.FAILED);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(provider.callCount(clientId)).isEqualTo(1);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(8));
        assertThat(getOrder(orderId).require().statusReason()).contains("did not respond within");
        assertThat(reservedToday(clientId)).isEqualByComparingTo("0.00");
    }

    private void awaitStatus(UUID orderId, OrderStatus expected) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(getOrder(orderId).require().status()).isEqualTo(expected));
    }
}
