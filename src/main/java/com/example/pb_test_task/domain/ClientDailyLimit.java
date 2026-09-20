package com.example.pb_test_task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Getter
@RequiredArgsConstructor(access = PROTECTED)
@Entity
@Table(name = "client_daily_limit")
@IdClass(ClientDailyLimit.Key.class)
public class ClientDailyLimit {

    @Id
    private UUID clientId;

    @Id
    private LocalDate businessDay;

    @Column(nullable = false)
    private BigDecimal dailyLimit;

    @Column(nullable = false)
    private BigDecimal reserved;

    public record Key(UUID clientId, LocalDate businessDay) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
