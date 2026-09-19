package com.example.pb_test_task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static jakarta.persistence.EnumType.STRING;
import static java.util.UUID.randomUUID;

@Getter
@Entity
@Table(name = "orders")
public class Order implements Persistable<UUID> {

    @Id
    @Getter
    private UUID id;

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDate businessDay;

    @Column(nullable = false)
    private boolean limitReleased;

    @Column(length = 512)
    private String statusReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean newlyCreated;

    protected Order() {
    }

    public static Order create(UUID clientId, BigDecimal amount, LocalDate businessDay, Instant now) {
        Order order = new Order();
        order.id = randomUUID();
        order.clientId = clientId;
        order.amount = amount;
        order.status = OrderStatus.NEW;
        order.businessDay = businessDay;
        order.limitReleased = false;
        order.createdAt = now;
        order.updatedAt = now;
        order.newlyCreated = true;
        return order;
    }

    @Override
    public boolean isNew() {
        return newlyCreated;
    }
}
