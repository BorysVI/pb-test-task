package com.example.pb_test_task.api.dto;

import com.example.pb_test_task.domain.Order;
import com.example.pb_test_task.domain.OrderStatus;
import com.example.pb_test_task.service.OrderService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID clientId,
        BigDecimal amount,
        OrderStatus status,
        String statusReason,
        Instant createdAt,
        Instant updatedAt,
        List<StatusHistoryItem> history
) {
    public static OrderResponse of(Order order) {
        return build(order, null);
    }

    public static OrderResponse of(OrderService.OrderView view) {
        return build(view.order(), view.history().stream().map(StatusHistoryItem::of).toList());
    }

    private static OrderResponse build(Order order, List<StatusHistoryItem> history) {
        return new OrderResponse(order.getId(), order.getClientId(), order.getAmount(), order.getStatus(),
                order.getStatusReason(), order.getCreatedAt(), order.getUpdatedAt(), history);
    }
}
