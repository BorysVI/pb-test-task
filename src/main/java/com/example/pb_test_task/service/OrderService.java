package com.example.pb_test_task.service;

import com.example.pb_test_task.config.OrderProperties;
import com.example.pb_test_task.domain.Order;
import com.example.pb_test_task.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final DailyLimitService dailyLimitService;
    private final IdempotencyService idempotencyService;
    private final Clock clock;
    private final OrderProperties properties;


    @Transactional
    public CreateResult create(UUID idempotencyKey, UUID clientId, BigDecimal amount) {
        String fingerprint = IdempotencyService.fingerprint(clientId, amount);
        var replayed = idempotencyService.lockAndFind(idempotencyKey, fingerprint)
                .map(record -> orderRepository.findById(record.getOrderId())
                        .orElseThrow(() -> new InconsistentStateException.OrphanedIdempotencyRecord(
                                idempotencyKey, record.getOrderId())
                        )
                );

        if (replayed.isPresent()) {
            return new CreateResult(replayed.get(), true);
        }

        Instant now = clock.instant();
        LocalDate businessDay = LocalDate.ofInstant(now, properties.timeZone());
        dailyLimitService.reserve(clientId, businessDay, amount);

        Order order = orderRepository.save(Order.create(clientId, amount, businessDay, now));
        idempotencyService.record(idempotencyKey, fingerprint, order.getId(), now);
        return new CreateResult(order, false);
    }

    public record CreateResult(Order order, boolean idempotentReplay) {}
}
