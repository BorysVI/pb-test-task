package com.example.pb_test_task.api.dto;

import com.example.pb_test_task.domain.OrderStatus;

import java.time.Instant;

public record StatusHistoryItem(OrderStatus fromStatus, OrderStatus toStatus, String reason, Instant createdAt) {
}
