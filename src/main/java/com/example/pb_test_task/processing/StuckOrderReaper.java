package com.example.pb_test_task.processing;

import com.example.pb_test_task.config.OrderProperties;
import com.example.pb_test_task.domain.Order;
import com.example.pb_test_task.repository.OrderRepository;
import com.example.pb_test_task.service.OrderRedriveService;
import com.example.pb_test_task.service.OrderTransitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.example.pb_test_task.domain.OrderStatus.FAILED;
import static com.example.pb_test_task.domain.OrderStatus.NEW;
import static com.example.pb_test_task.domain.OrderStatus.PROCESSING;

@Component
@Slf4j
public class StuckOrderReaper {

    private static final String ABANDONED_REASON = "Abandoned in PROCESSING, released by the reaper";
    private static final String GIVE_UP_REASON = "Never picked up for processing, released by the reaper";

    private final OrderRepository orderRepository;
    private final OrderTransitionService transitionService;
    private final OrderRedriveService redriveService;
    private final Clock clock;
    private final Duration threshold;
    private final Duration newThreshold;
    private final Duration giveUpAfter;

    public StuckOrderReaper(OrderRepository orderRepository,
                            OrderTransitionService transitionService,
                            OrderRedriveService redriveService,
                            Clock clock,
                            OrderProperties properties) {
        this.orderRepository = orderRepository;
        this.transitionService = transitionService;
        this.redriveService = redriveService;
        this.clock = clock;
        this.threshold = properties.reaper().threshold();
        this.newThreshold = properties.reaper().newThreshold();
        this.giveUpAfter = properties.reaper().giveUpAfter();
    }

    @Scheduled(fixedDelayString = "${orders.reaper.interval}")
    public void reap() {
        List<Order> stuck = orderRepository.findStuckInProcessing(clock.instant().minus(threshold));
        stuck.forEach(order -> {
            if (transitionService.transition(order, PROCESSING, FAILED, ABANDONED_REASON)) {
                log.warn("Reaped order {} stuck in PROCESSING since {}", order.getId(), order.getUpdatedAt());
            }
        });
    }

    @Scheduled(fixedDelayString = "${orders.reaper.interval}")
    public void redriveAbandoned() {
        Instant now = clock.instant();
        Instant staleBefore = now.minus(newThreshold);
        orderRepository.findStuckInNew(staleBefore).forEach(order -> {
            if (order.getCreatedAt().isBefore(now.minus(giveUpAfter))) {
                giveUp(order);
            } else if (redriveService.redrive(order, staleBefore, now)) {
                log.warn("Redriving order {} not picked up since {}", order.getId(), order.getUpdatedAt());
            }
        });
    }

    private void giveUp(Order order) {
        if (transitionService.transition(order, NEW, FAILED, GIVE_UP_REASON)) {
            log.error("Order {} was never picked up since {}, giving up", order.getId(), order.getCreatedAt());
        }
    }
}
