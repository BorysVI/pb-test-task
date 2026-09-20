package com.example.pb_test_task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Getter
@NoArgsConstructor(access = PROTECTED)
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus toStatus;

    @Column(length = 512)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt;

    public static OrderStatusHistory of(UUID orderId, OrderStatus from, OrderStatus to, String reason, Instant now) {
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.orderId = orderId;
        entry.fromStatus = from;
        entry.toStatus = to;
        entry.reason = reason;
        entry.createdAt = now;
        return entry;
    }
}
