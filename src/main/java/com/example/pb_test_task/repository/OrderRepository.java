package com.example.pb_test_task.repository;

import com.example.pb_test_task.domain.Order;
import com.example.pb_test_task.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Order o
               SET o.status = :to, o.statusReason = :reason, o.updatedAt = :now
             WHERE o.id = :id AND o.status = :from
            """)
    int compareAndSetStatus(@Param("id") UUID id,
                            @Param("from") OrderStatus from,
                            @Param("to") OrderStatus to,
                            @Param("reason") String reason,
                            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Order o SET o.limitReleased = true WHERE o.id = :id AND o.limitReleased = false")
    int markLimitReleased(@Param("id") UUID id);
}
