package com.example.pb_test_task.provider;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Component
public class ProviderTimeLimiter implements AutoCloseable {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public <T> T callWithin(Duration timeout, Supplier<T> call) {
        Future<T> future = executor.submit(call::get);
        try {
            return future.get(timeout.toMillis(), MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ProviderException.Timeout("Provider did not respond within " + timeout.toMillis() + "ms");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ProviderException.Timeout("Waiting for the provider was interrupted");
        } catch (ExecutionException e) {
            throw switch (e.getCause()) {
                case RuntimeException runtime -> runtime;
                case Throwable other -> new IllegalStateException(other);
            };
        }
    }

    @Override
    public void close() {
        executor.close();
    }
}
