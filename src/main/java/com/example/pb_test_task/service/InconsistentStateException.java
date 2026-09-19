package com.example.pb_test_task.service;

import java.util.UUID;

public sealed class InconsistentStateException extends RuntimeException {

    private InconsistentStateException(String message) {
        super(message);
    }

    public static final class OrphanedIdempotencyRecord extends InconsistentStateException {
        public OrphanedIdempotencyRecord(UUID idempotencyKey, UUID orderId) {
            super("Idempotency record " + idempotencyKey + " references missing order " + orderId);
        }
    }
}
