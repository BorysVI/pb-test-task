package com.example.pb_test_task;

import com.example.pb_test_task.domain.OrderStatus;
import com.example.pb_test_task.domain.OutboxMessage;
import com.example.pb_test_task.event.OrderRoutingKey;
import com.example.pb_test_task.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CancelOrderIT extends IntegrationTestBase {

    private static final BigDecimal AMOUNT = new BigDecimal("3000.00");

    @Test
    void cancellingAnUnprocessedOrderReleasesItsShareOfTheDailyLimit() {
        UUID clientId = randomUUID();

        withProcessingPaused(() -> {
            UUID orderId = createOrder(randomUUID(), clientId, AMOUNT).require().id();
            assertThat(reservedToday(clientId)).isEqualByComparingTo(AMOUNT);

            var cancelled = cancelOrder(orderId);

            assertThat(cancelled.status().value()).isEqualTo(200);
            assertThat(cancelled.require().status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(cancelled.require().history())
                    .extracting(item -> item.toStatus().name())
                    .containsExactly("NEW", "CANCELLED");
            assertThat(reservedToday(clientId)).isEqualByComparingTo("0.00");
            assertThat(outboxRepository.findByAggregateIdOrderByIdAsc(orderId))
                    .extracting(OutboxMessage::getRoutingKey)
                    .containsExactly(OrderRoutingKey.CREATED, OrderRoutingKey.CANCELLED);
        });
    }

    @Test
    void anAlreadyProcessedOrderCanNoLongerBeCancelled() {
        UUID clientId = randomUUID();
        UUID orderId = createOrder(randomUUID(), clientId, AMOUNT).require().id();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(getOrder(orderId).require().status()).isEqualTo(OrderStatus.COMPLETED));

        assertThat(cancelOrder(orderId).status().value()).isEqualTo(409);
        assertThat(reservedToday(clientId)).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void concurrentCancellationsReleaseTheLimitExactlyOnce() {
        UUID clientId = randomUUID();

        withProcessingPaused(() -> {
            UUID orderId = createOrder(randomUUID(), clientId, AMOUNT).require().id();
            CountDownLatch startGate = new CountDownLatch(1);

            List<ApiResult<com.example.pb_test_task.api.dto.OrderResponse>> results;
            try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
                List<Future<ApiResult<com.example.pb_test_task.api.dto.OrderResponse>>> futures =
                        IntStream.range(0, 10)
                                .<Callable<ApiResult<com.example.pb_test_task.api.dto.OrderResponse>>>mapToObj(
                                        _ -> () -> {
                                            startGate.await();
                                            return cancelOrder(orderId);
                                        })
                                .map(pool::submit)
                                .toList();
                startGate.countDown();
                results = futures.stream().map(CancelOrderIT::get).toList();
            }

            assertThat(results).filteredOn(result -> result.status().value() == 200).hasSize(1);
            assertThat(results).filteredOn(result -> result.status().value() == 409).hasSize(9);
            assertThat(reservedToday(clientId)).isEqualByComparingTo("0.00");
        });
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
