package com.example.pb_test_task.service;

import java.util.UUID;

public sealed class OrderException extends RuntimeException {

    private OrderException(String message) {
        super(message);
    }

    public static final class DailyLimitExceeded extends OrderException {
        public DailyLimitExceeded(UUID clientId) {
            super("Daily limit exhausted for client " + clientId);
        }
    }

    public static final class IdempotencyKeyReused extends OrderException {
        public IdempotencyKeyReused(UUID key) {
            super("Idempotency key " + key + " was already used with a different request payload");
        }
    }

    public static final class OrderNotFound extends OrderException {
        public OrderNotFound(UUID orderId) {
            super("Order " + orderId + " not found");
        }
    }

    public static final class OrderNotCancellable extends OrderException {
        public OrderNotCancellable(UUID orderId) {
            super("Order " + orderId + " is already being processed and can no longer be cancelled");
        }
    }
}
