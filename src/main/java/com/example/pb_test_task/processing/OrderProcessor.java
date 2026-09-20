package com.example.pb_test_task.processing;

import com.example.pb_test_task.domain.Order;
import com.example.pb_test_task.domain.OrderStatus;
import com.example.pb_test_task.provider.ProviderException;
import com.example.pb_test_task.provider.ResilientProviderClient;
import com.example.pb_test_task.repository.OrderRepository;
import com.example.pb_test_task.service.OrderTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

import static com.example.pb_test_task.domain.OrderStatus.COMPLETED;
import static com.example.pb_test_task.domain.OrderStatus.FAILED;
import static com.example.pb_test_task.domain.OrderStatus.NEW;
import static com.example.pb_test_task.domain.OrderStatus.PROCESSING;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderProcessor {

    private final OrderRepository orderRepository;
    private final OrderTransitionService transitionService;
    private final ResilientProviderClient providerClient;

    public void process(UUID orderId) {
        Optional<Order> claimed = claim(orderId);
        if (claimed.isEmpty()) {
            log.debug("Order {} is no longer NEW, skipping", orderId);
            return;
        }

        Order order = claimed.get();
        try {
            String reference = providerClient.charge(order.getId(), order.getClientId(), order.getAmount());
            settle(order, COMPLETED, reference);
        } catch (ProviderException e) {
            log.warn("Order {} failed at the provider: {}", orderId, e.getMessage());
            settle(order, FAILED, e.getMessage());
        }
    }

    private Optional<Order> claim(UUID orderId) {
        return orderRepository.findById(orderId)
                .filter(order -> transitionService.transition(order, NEW, PROCESSING, null));
    }

    private void settle(Order order, OrderStatus outcome, String reason) {
        if (transitionService.transition(order, PROCESSING, outcome, reason)) {
            return;
        }
        if (outcome == COMPLETED) {
            log.error("Order {} was charged ({}) but had already left PROCESSING; needs reconciliation",
                    order.getId(), reason);
        } else {
            log.warn("Order {} had already left PROCESSING before the failure was recorded: {}",
                    order.getId(), reason);
        }
    }
}
