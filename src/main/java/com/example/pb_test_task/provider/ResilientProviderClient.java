package com.example.pb_test_task.provider;

import com.example.pb_test_task.config.OrderProperties;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Component
public class ResilientProviderClient {

    private final PaymentProvider provider;
    private final ProviderTimeLimiter timeLimiter;
    private final Duration timeout;

    public ResilientProviderClient(PaymentProvider provider,
                                   ProviderTimeLimiter timeLimiter,
                                   OrderProperties properties) {
        this.provider = provider;
        this.timeLimiter = timeLimiter;
        this.timeout = properties.provider().timeout();
    }

    @Retryable(includes = ProviderException.ServerError.class,
            maxRetriesString = "${orders.provider.max-retries}",
            delayString = "${orders.provider.retry-delay}",
            multiplier = 2.0,
            jitter = 50)
    public String charge(UUID orderId, UUID clientId, BigDecimal amount) {
        return timeLimiter.callWithin(timeout, () -> provider.charge(orderId, clientId, amount));
    }
}
