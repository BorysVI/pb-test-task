package com.example.pb_test_task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    private UUID idempotencyKey;

    @Column(nullable = false, length = 128)
    private String requestFingerprint;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private Instant createdAt;

    public static IdempotencyRecord of(UUID key, String fingerprint, UUID orderId, Instant now) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.idempotencyKey = key;
        record.requestFingerprint = fingerprint;
        record.orderId = orderId;
        record.createdAt = now;
        return record;
    }
}
