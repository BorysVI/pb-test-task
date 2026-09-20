package com.example.pb_test_task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Getter
@NoArgsConstructor(access = PROTECTED)
@Entity
@Table(name = "outbox_message")
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 128)
    private String routingKey;

    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant publishedAt;

    public static OutboxMessage pending(UUID aggregateId, String eventType, String routingKey,
                                        String payload, Instant now) {
        OutboxMessage message = new OutboxMessage();
        message.aggregateId = aggregateId;
        message.eventType = eventType;
        message.routingKey = routingKey;
        message.payload = payload;
        message.createdAt = now;
        return message;
    }
}
