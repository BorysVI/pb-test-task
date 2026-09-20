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

class IdempotencyConcurrencyIT extends IntegrationTestBase {

    private static final int THREADS = 20;
    private static final BigDecimal AMOUNT = new BigDecimal("1500.00");

    @Test
    void concurrentRequestsSharingAnIdempotencyKeyCreateExactlyOneOrder() {
        UUID clientId = randomUUID();
        UUID idempotencyKey = randomUUID();
        CountDownLatch startGate = new CountDownLatch(1);

        List<ApiResult<OrderResponse>> results;
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Callable<ApiResult<OrderResponse>>> attempts = IntStream.range(0, THREADS)
                    .<Callable<ApiResult<OrderResponse>>>mapToObj(_ -> () -> {
                        startGate.await();
                        return createOrder(idempotencyKey, clientId, AMOUNT);
                    })
                    .toList();
            List<Future<ApiResult<OrderResponse>>> futures = attempts.stream().map(pool::submit).toList();
            startGate.countDown();
            results = futures.stream().map(IdempotencyConcurrencyIT::get).toList();
        }

        assertThat(results).allMatch(result -> result.status().is2xxSuccessful());
        assertThat(results).filteredOn(result -> result.status().value() == 201).hasSize(1);
        assertThat(results).filteredOn(result -> result.status().value() == 200).hasSize(THREADS - 1);
        assertThat(results).extracting(result -> result.require().id()).containsOnly(results.getFirst().require().id());

        assertThat(orderRepository.findAll()).filteredOn(order -> order.getClientId().equals(clientId)).hasSize(1);
        assertThat(reservedToday(clientId)).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void reusingAKeyWithADifferentPayloadIsRejected() {
        UUID clientId = randomUUID();
        UUID idempotencyKey = randomUUID();

        assertThat(createOrder(idempotencyKey, clientId, AMOUNT).status().value()).isEqualTo(201);
        assertThat(createOrder(idempotencyKey, clientId, new BigDecimal("9999.00")).status().value()).isEqualTo(422);
        assertThat(reservedToday(clientId)).isEqualByComparingTo(AMOUNT);
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
