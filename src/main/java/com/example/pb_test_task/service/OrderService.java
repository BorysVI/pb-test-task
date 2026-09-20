package com.example.pb_test_task.service;

import com.example.pb_test_task.domain.Order;
import com.example.pb_test_task.domain.OrderStatusHistory;
import com.example.pb_test_task.repository.OrderRepository;
import com.example.pb_test_task.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.example.pb_test_task.domain.OrderStatus.CANCELLED;
import static com.example.pb_test_task.domain.OrderStatus.NEW;

@RequiredArgsConstructor
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final DailyLimitService dailyLimitService;
    private final IdempotencyService idempotencyService;
    private final OrderTransitionService transitionService;
    private final Clock clock;

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
        LocalDate businessDay = LocalDate.ofInstant(now, clock.getZone());
        dailyLimitService.reserve(clientId, businessDay, amount);

        Order order = orderRepository.save(Order.create(clientId, amount, businessDay, now));
        transitionService.recordCreation(order);
        idempotencyService.record(idempotencyKey, fingerprint, order.getId(), now);
        return new CreateResult(order, false);
    }

    @Transactional(readOnly = true)
    public OrderView find(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderException.OrderNotFound(orderId));
        return new OrderView(order, historyRepository.findByOrderIdOrderByIdAsc(orderId));
    }

    @Transactional
    public OrderView cancel(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderException.OrderNotFound(orderId));
        if (!transitionService.transition(order, NEW, CANCELLED, "Cancelled by client")) {
            throw new OrderException.OrderNotCancellable(orderId);
        }
        return find(orderId);
    }

    public record CreateResult(Order order, boolean idempotentReplay) {}

    public record OrderView(Order order, List<OrderStatusHistory> history) {}
}
