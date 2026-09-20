package com.example.pb_test_task.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

@Validated
@ConfigurationProperties("orders")
public record OrderProperties(
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal dailyLimit,
        @NotNull ZoneId timeZone,
        @NotNull @Valid Provider provider,
        @NotNull @Valid Outbox outbox
) {
    public record Provider(
            @NotNull Duration timeout,
            @NotNull Integer maxRetries,
            @NotNull Duration retryDelay,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double successRate,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double serverErrorRate,
            @NotNull Duration hangDuration
    ) {}

    public record Outbox(
            @NotNull Duration pollInterval,
            @NotNull Duration confirmTimeout,
            @NotNull @Positive Integer batchSize
    ) {}
}
