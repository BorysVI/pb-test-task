package com.example.pb_test_task.service;

import com.example.pb_test_task.config.OrderProperties;
import com.example.pb_test_task.repository.ClientDailyLimitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.transaction.annotation.Propagation.MANDATORY;

@Service
public class DailyLimitService {

    private final ClientDailyLimitRepository limitRepository;
    private final BigDecimal defaultLimit;

    public DailyLimitService(ClientDailyLimitRepository limitRepository,
                             OrderProperties properties) {
        this.limitRepository = limitRepository;
        this.defaultLimit = properties.dailyLimit();
    }

    @Transactional(propagation = MANDATORY)
    public void reserve(UUID clientId, LocalDate businessDay, BigDecimal amount) {
        limitRepository.ensureCounterExists(clientId, businessDay, defaultLimit);
        if (limitRepository.tryReserve(clientId, businessDay, amount) == 0) {
            throw new OrderException.DailyLimitExceeded(clientId);
        }
    }
}
