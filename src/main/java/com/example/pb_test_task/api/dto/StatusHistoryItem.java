package com.example.pb_test_task.api.dto;

import com.example.pb_test_task.domain.OrderStatus;
import com.example.pb_test_task.domain.OrderStatusHistory;

import java.time.Instant;

public record StatusHistoryItem(OrderStatus fromStatus, OrderStatus toStatus, String reason, Instant createdAt) {

    public static StatusHistoryItem of(OrderStatusHistory entry) {
        return new StatusHistoryItem(entry.getFromStatus(), entry.getToStatus(), entry.getReason(),
                entry.getCreatedAt());
    }
}
