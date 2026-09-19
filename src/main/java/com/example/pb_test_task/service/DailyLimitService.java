package com.example.pb_test_task.service;

import com.example.pb_test_task.config.OrderProperties;
import com.example.pb_test_task.repository.ClientDailyLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.transaction.annotation.Propagation.MANDATORY;

@RequiredArgsConstructor
@Service
public class DailyLimitService {

    private final ClientDailyLimitRepository limitRepository;
    private final OrderProperties properties;

    @Transactional(propagation = MANDATORY)
    public void reserve(UUID clientId, LocalDate businessDay, BigDecimal amount) {
        limitRepository.ensureCounterExists(clientId, businessDay, properties.dailyLimit());
        if (limitRepository.tryReserve(clientId, businessDay, amount) == 0) {
            throw new OrderException.DailyLimitExceeded(clientId);
        }
    }
}
