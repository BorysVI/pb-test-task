package com.example.pb_test_task.provider;

public sealed class ProviderException extends RuntimeException {

    private ProviderException(String message) {
        super(message);
    }

    public static final class ServerError extends ProviderException {
        public ServerError(String message) {
            super(message);
        }
    }

    public static final class Timeout extends ProviderException {
        public Timeout(String message) {
            super(message);
        }
    }
}
