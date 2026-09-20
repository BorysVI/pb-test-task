package com.example.pb_test_task.support;

import com.example.pb_test_task.provider.PaymentProvider;
import com.example.pb_test_task.provider.ProviderException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ScriptedPaymentProvider implements PaymentProvider {

    public enum Outcome {SUCCESS, SERVER_ERROR, HANG}

    private final Map<UUID, Queue<Outcome>> scripts = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> callCounts = new ConcurrentHashMap<>();
    private final Duration hangDuration = Duration.ofSeconds(11);

    public void script(UUID clientId, Outcome... outcomes) {
        scripts.put(clientId, new ConcurrentLinkedQueue<>(java.util.List.of(outcomes)));
    }

    public int callCount(UUID clientId) {
        return callCounts.getOrDefault(clientId, new AtomicInteger()).get();
    }

    @Override
    public String charge(UUID orderId, UUID clientId, BigDecimal amount) {
        callCounts.computeIfAbsent(clientId, key -> new AtomicInteger()).incrementAndGet();
        return switch (nextOutcome(clientId)) {
            case SUCCESS -> "TXN-" + orderId;
            case SERVER_ERROR -> throw new ProviderException.ServerError("scripted 500 for order " + orderId);
            case HANG -> hang(orderId);
        };
    }

    private Outcome nextOutcome(UUID clientId) {
        Queue<Outcome> script = scripts.get(clientId);
        if (script == null) {
            return Outcome.SUCCESS;
        }
        Outcome next = script.poll();
        return next == null ? Outcome.SUCCESS : next;
    }

    private String hang(UUID orderId) {
        try {
            Thread.sleep(hangDuration);
            return "TXN-" + orderId;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException.Timeout("scripted hang interrupted");
        }
    }
}
