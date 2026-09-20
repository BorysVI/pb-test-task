package com.example.pb_test_task.service;

import com.example.pb_test_task.domain.IdempotencyRecord;
import com.example.pb_test_task.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.transaction.annotation.Propagation.MANDATORY;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;

    @Transactional(propagation = MANDATORY)
    public Optional<IdempotencyRecord> lockAndFind(UUID key, String fingerprint) {
        repository.acquireTransactionLock(key.getMostSignificantBits() ^ key.getLeastSignificantBits());
        Optional<IdempotencyRecord> existing = repository.findById(key);
        existing.filter(record -> !record.getRequestFingerprint().equals(fingerprint))
                .ifPresent(_ -> {
                    throw new OrderException.IdempotencyKeyReused(key);
                });
        return existing;
    }

    @Transactional(propagation = MANDATORY)
    public void record(UUID key, String fingerprint, UUID orderId, Instant now) {
        repository.save(IdempotencyRecord.of(key, fingerprint, orderId, now));
    }

    public static String fingerprint(UUID clientId, BigDecimal amount) {
        return clientId + ":" + amount.stripTrailingZeros().toPlainString();
    }
}
