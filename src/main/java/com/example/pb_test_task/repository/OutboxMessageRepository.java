package com.example.pb_test_task.repository;

import com.example.pb_test_task.domain.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findByAggregateIdOrderByIdAsc(UUID aggregateId);

    @Query(value = """
            SELECT * FROM outbox_message
             WHERE published_at IS NULL
             ORDER BY id
             FOR UPDATE SKIP LOCKED
             LIMIT :batchSize
            """, nativeQuery = true)
    List<OutboxMessage> lockUnpublishedBatch(@Param("batchSize") int batchSize);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OutboxMessage m SET m.publishedAt = :now WHERE m.id IN :ids")
    void markPublished(@Param("ids") Collection<Long> ids, @Param("now") Instant now);
}
