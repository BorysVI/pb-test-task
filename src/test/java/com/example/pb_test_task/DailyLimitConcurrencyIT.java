package com.example.pb_test_task;

import com.example.pb_test_task.api.dto.OrderResponse;
import com.example.pb_test_task.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

class DailyLimitConcurrencyIT extends IntegrationTestBase {

    private static final int THREADS = 30;
    private static final BigDecimal AMOUNT = new BigDecimal("2000.00");

    @Test
    void concurrentRequestsFromOneClientNeverOversubscribeTheDailyLimit() {
        BigDecimal dailyLimit = properties.dailyLimit();
        int expectedAccepted = dailyLimit.divideToIntegralValue(AMOUNT).intValueExact();
        assertThat(expectedAccepted).isLessThan(THREADS);

        UUID clientId = randomUUID();
        CountDownLatch startGate = new CountDownLatch(1);

        List<ApiResult<OrderResponse>> results;
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Callable<ApiResult<OrderResponse>>> attempts = IntStream.range(0, THREADS)
                    .<Callable<ApiResult<OrderResponse>>>mapToObj(_ -> () -> {
                        startGate.await();
                        return createOrder(randomUUID(), clientId, AMOUNT);
                    })
                    .toList();
            List<Future<ApiResult<OrderResponse>>> futures = attempts.stream().map(pool::submit).toList();
            startGate.countDown();
            results = futures.stream().map(DailyLimitConcurrencyIT::get).toList();
        }

        assertThat(results).filteredOn(result -> result.status().value() == 201).hasSize(expectedAccepted);
        assertThat(results).filteredOn(result -> result.status().value() == 409)
                .hasSize(THREADS - expectedAccepted);
        assertThat(results).noneMatch(result -> result.status().is5xxServerError());

        assertThat(reservedToday(clientId)).isEqualByComparingTo(AMOUNT.multiply(BigDecimal.valueOf(expectedAccepted)));
        assertThat(orderRepository.findAll())
                .filteredOn(order -> order.getClientId().equals(clientId))
                .hasSize(expectedAccepted);
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
