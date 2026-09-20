package com.example.pb_test_task;

import com.example.pb_test_task.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

class PerClientDailyLimitIT extends IntegrationTestBase {

    @Test
    void reservationsHonourTheRowLevelLimitRatherThanTheConfiguredDefault() {
        UUID clientId = randomUUID();
        BigDecimal tightLimit = new BigDecimal("3000.00");

        withProcessingPaused(() -> {
            assertThat(createOrder(randomUUID(), clientId, new BigDecimal("1000.00")).status().value())
                    .isEqualTo(201);
            narrowLimitTo(clientId, tightLimit);

            assertThat(createOrder(randomUUID(), clientId, new BigDecimal("2000.00")).status().value())
                    .isEqualTo(201);
            assertThat(createOrder(randomUUID(), clientId, new BigDecimal("1.00")).status().value())
                    .isEqualTo(409);

            assertThat(reservedToday(clientId)).isEqualByComparingTo(tightLimit);
            assertThat(configuredLimitFor(clientId)).isEqualByComparingTo(tightLimit);
        });
    }

    @Test
    void anUnconfiguredClientGetsTheConfiguredDefault() {
        UUID clientId = randomUUID();

        withProcessingPaused(() ->
                assertThat(createOrder(randomUUID(), clientId, new BigDecimal("100.00")).status().value())
                        .isEqualTo(201));

        assertThat(configuredLimitFor(clientId)).isEqualByComparingTo(properties.dailyLimit());
    }

    private void narrowLimitTo(UUID clientId, BigDecimal limit) {
        jdbcTemplate.update(
                "UPDATE client_daily_limit SET daily_limit = ? WHERE client_id = ? AND business_day = ?",
                limit, clientId, today());
    }

    private BigDecimal configuredLimitFor(UUID clientId) {
        return jdbcTemplate.queryForObject(
                "SELECT daily_limit FROM client_daily_limit WHERE client_id = ? AND business_day = ?",
                BigDecimal.class, clientId, today());
    }
}
