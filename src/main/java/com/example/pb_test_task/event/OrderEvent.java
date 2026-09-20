package com.example.pb_test_task.event;

import com.example.pb_test_task.domain.Order;
import com.example.pb_test_task.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderEvent(
        UUID orderId,
        UUID clientId,
        BigDecimal amount,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String reason,
        Instant occurredAt
) {
    public static OrderEvent created(Order order, Instant occurredAt) {
        return new OrderEvent(order.getId(), order.getClientId(), order.getAmount(),
                null, OrderStatus.NEW, null, occurredAt);
    }

    public String eventType() {
        return switch (toStatus) {
            case NEW -> "OrderCreated";
            case PROCESSING -> "OrderProcessingStarted";
            case COMPLETED -> "OrderCompleted";
            case FAILED -> "OrderFailed";
            case CANCELLED -> "OrderCancelled";
        };
    }

    public String routingKey() {
        return switch (toStatus) {
            case NEW -> OrderRoutingKey.CREATED;
            case PROCESSING -> OrderRoutingKey.PROCESSING_STARTED;
            case COMPLETED -> OrderRoutingKey.COMPLETED;
            case FAILED -> OrderRoutingKey.FAILED;
            case CANCELLED -> OrderRoutingKey.CANCELLED;
        };
    }
}
