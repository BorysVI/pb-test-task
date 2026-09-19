package com.example.pb_test_task.config;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.ZoneId;

@Validated
@ConfigurationProperties("orders")
public record OrderProperties(
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal dailyLimit,
        @NotNull ZoneId timeZone
) {}
