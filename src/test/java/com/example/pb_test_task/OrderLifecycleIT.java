package com.example.pb_test_task;

import com.example.pb_test_task.api.dto.OrderResponse;
import com.example.pb_test_task.domain.OrderStatus;
import com.example.pb_test_task.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OrderLifecycleIT extends IntegrationTestBase {

    @Test
    void createdOrderIsProcessedAsynchronouslyAndKeepsAnAuditTrail() {
        UUID clientId = randomUUID();

        var created = createOrder(randomUUID(), clientId, new BigDecimal("1000.00"));

        assertThat(created.status().value()).isEqualTo(201);
        UUID orderId = created.require().id();
        assertThat(created.require().status()).isEqualTo(OrderStatus.NEW);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(getOrder(orderId).require().status()).isEqualTo(OrderStatus.COMPLETED));

        OrderResponse finalState = getOrder(orderId).require();
        assertThat(finalState.history())
                .extracting(item -> item.toStatus().name())
                .containsExactly("NEW", "PROCESSING", "COMPLETED");
        assertThat(reservedToday(clientId)).isEqualByComparingTo("1000.00");
    }
}
