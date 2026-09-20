package com.example.pb_test_task.service;

import com.example.pb_test_task.domain.Order;
import com.example.pb_test_task.event.OrderEvent;
import com.example.pb_test_task.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@RequiredArgsConstructor
@Service
public class OrderRedriveService {

    private final OrderRepository orderRepository;
    private final OutboxAppender outboxAppender;

    @Transactional
    public boolean redrive(Order order, Instant staleBefore, Instant now) {
        if (orderRepository.claimForRedrive(order.getId(), staleBefore, now) == 0) {
            return false;
        }
        outboxAppender.append(OrderEvent.created(order, now));
        return true;
    }
}
