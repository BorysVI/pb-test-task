package com.example.pb_test_task.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull
        UUID clientId,
        @NotNull
        @DecimalMin(value = "0", inclusive = false, message = "amount must be greater than 0")
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount
) {}
