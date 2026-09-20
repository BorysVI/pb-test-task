package com.example.pb_test_task.provider;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentProvider {

    String charge(UUID orderId, UUID clientId, BigDecimal amount);
}
