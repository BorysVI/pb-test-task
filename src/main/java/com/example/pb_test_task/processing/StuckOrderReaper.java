package com.example.pb_test_task.processing;

import com.example.pb_test_task.config.OrderProperties;
import com.example.pb_test_task.domain.Order;
import com.example.pb_test_task.repository.OrderRepository;
import com.example.pb_test_task.service.OrderTransitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static com.example.pb_test_task.domain.OrderStatus.FAILED;
import static com.example.pb_test_task.domain.OrderStatus.PROCESSING;

@Component
@Slf4j
public class StuckOrderReaper {

    private static final String REASON = "Abandoned in PROCESSING, released by the reaper";

    private final OrderRepository orderRepository;
    private final OrderTransitionService transitionService;
    private final Clock clock;
    private final Duration threshold;

    public StuckOrderReaper(OrderRepository orderRepository,
                            OrderTransitionService transitionService,
                            Clock clock,
                            OrderProperties properties) {
        this.orderRepository = orderRepository;
        this.transitionService = transitionService;
        this.clock = clock;
        this.threshold = properties.reaper().threshold();
    }

    @Scheduled(fixedDelayString = "${orders.reaper.interval}")
    public void reap() {
        List<Order> stuck = orderRepository.findStuckInProcessing(clock.instant().minus(threshold));
        stuck.forEach(order -> {
            if (transitionService.transition(order, PROCESSING, FAILED, REASON)) {
                log.warn("Reaped order {} stuck in PROCESSING since {}", order.getId(), order.getUpdatedAt());
            }
        });
    }
}
