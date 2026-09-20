package com.example.pb_test_task.provider;

import com.example.pb_test_task.config.OrderProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.random.RandomGenerator;

import static java.util.UUID.randomUUID;

@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    private final RandomGenerator random;
    private final OrderProperties.Provider settings;

    public SimulatedPaymentProvider(RandomGenerator random, OrderProperties properties) {
        this.random = random;
        this.settings = properties.provider();
    }

    @Override
    public String charge(UUID orderId, UUID clientId, BigDecimal amount) {
        double roll = random.nextDouble();
        if (roll < settings.successRate()) {
            return "TXN-" + randomUUID();
        }
        if (roll < settings.successRate() + settings.serverErrorRate()) {
            throw new ProviderException.ServerError("Provider returned 500 for order " + orderId);
        }
        hang();
        return "TXN-" + randomUUID();
    }

    private void hang() {
        try {
            Thread.sleep(settings.hangDuration());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException.Timeout("Provider call was interrupted");
        }
    }
}
