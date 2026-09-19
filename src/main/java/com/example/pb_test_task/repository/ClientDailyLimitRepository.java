package com.example.pb_test_task.repository;

import com.example.pb_test_task.domain.ClientDailyLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface ClientDailyLimitRepository extends JpaRepository<ClientDailyLimit, ClientDailyLimit.Key> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO client_daily_limit (client_id, business_day, daily_limit, reserved)
            VALUES (:clientId, :businessDay, :defaultLimit, 0)
            ON CONFLICT (client_id, business_day) DO NOTHING
            """, nativeQuery = true)
    void ensureCounterExists(@Param("clientId") UUID clientId,
                             @Param("businessDay") LocalDate businessDay,
                             @Param("defaultLimit") BigDecimal defaultLimit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE client_daily_limit
               SET reserved = reserved + :amount
             WHERE client_id = :clientId
               AND business_day = :businessDay
               AND reserved + :amount <= daily_limit
            """, nativeQuery = true)
    int tryReserve(@Param("clientId") UUID clientId,
                   @Param("businessDay") LocalDate businessDay,
                   @Param("amount") BigDecimal amount);
}
