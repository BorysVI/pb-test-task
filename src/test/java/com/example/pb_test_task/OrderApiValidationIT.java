package com.example.pb_test_task;

import com.example.pb_test_task.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static java.math.BigDecimal.ZERO;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

class OrderApiValidationIT extends IntegrationTestBase {

    @Test
    void amountMustBeStrictlyPositive() {
        assertThat(createOrder(randomUUID(), randomUUID(), ZERO).status().value()).isEqualTo(400);
        assertThat(createOrder(randomUUID(), randomUUID(), new BigDecimal("-1.00")).status().value()).isEqualTo(400);
    }

    @Test
    void theIdempotencyHeaderIsMandatory() {
        assertThat(createOrderWithoutIdempotencyKey(randomUUID(), new BigDecimal("10.00")).status().value())
                .isEqualTo(400);
    }

    @Test
    void anUnknownOrderIsReportedAsNotFound() {
        assertThat(getOrder(randomUUID()).status().value()).isEqualTo(404);
        assertThat(cancelOrder(randomUUID()).status().value()).isEqualTo(404);
    }
}
