package com.example.pb_test_task.repository;

import com.example.pb_test_task.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:lockKey)) locked", nativeQuery = true)
    void acquireTransactionLock(@Param("lockKey") long lockKey);
}
