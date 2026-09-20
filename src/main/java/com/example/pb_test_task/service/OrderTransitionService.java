package com.example.pb_test_task.service;

import com.example.pb_test_task.domain.Order;
import com.example.pb_test_task.domain.OrderStatus;
import com.example.pb_test_task.domain.OrderStatusHistory;
import com.example.pb_test_task.event.OrderEvent;
import com.example.pb_test_task.repository.OrderRepository;
import com.example.pb_test_task.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static com.example.pb_test_task.domain.OrderStatus.CANCELLED;
import static com.example.pb_test_task.domain.OrderStatus.FAILED;
import static com.example.pb_test_task.domain.OrderStatus.NEW;
import static org.springframework.transaction.annotation.Propagation.MANDATORY;

@RequiredArgsConstructor
@Service
public class OrderTransitionService {

    private static final Set<OrderStatus> RELEASES_LIMIT_STATUSES = EnumSet.of(FAILED, CANCELLED);

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OutboxAppender outboxAppender;
    private final DailyLimitService dailyLimitService;
    private final Clock clock;

    @Transactional
    public boolean transition(Order order, OrderStatus from, OrderStatus to, String reason) {
        Instant now = clock.instant();
        if (orderRepository.compareAndSetStatus(order.getId(), from, to, reason, now) == 0) {
            return false;
        }
        recordTransition(order, from, to, reason, now);
        if (RELEASES_LIMIT_STATUSES.contains(to)) {
            dailyLimitService.releaseOnce(order);
        }
        return true;
    }

    @Transactional(propagation = MANDATORY)
    public void recordCreation(Order order) {
        recordTransition(order, null, NEW, null, order.getCreatedAt());
    }

    private void recordTransition(Order order, OrderStatus from, OrderStatus to, String reason, Instant now) {
        historyRepository.save(OrderStatusHistory.of(order.getId(), from, to, reason, now));
        outboxAppender.append(new OrderEvent(order.getId(), order.getClientId(), order.getAmount(), from, to, reason, now));
    }
}
