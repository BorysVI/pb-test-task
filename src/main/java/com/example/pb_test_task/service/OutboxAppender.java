package com.example.pb_test_task.service;

import com.example.pb_test_task.domain.OutboxMessage;
import com.example.pb_test_task.event.OrderEvent;
import com.example.pb_test_task.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.transaction.annotation.Propagation.MANDATORY;

@RequiredArgsConstructor
@Component
public class OutboxAppender {

    private final OutboxMessageRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = MANDATORY)
    public void append(OrderEvent event) {
        repository.save(OutboxMessage.pending(event.orderId(), event.eventType(), event.routingKey(),
                objectMapper.writeValueAsString(event), event.occurredAt()));
    }
}
