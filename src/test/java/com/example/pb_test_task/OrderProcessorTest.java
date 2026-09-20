package com.example.pb_test_task;

import com.example.pb_test_task.domain.Order;
import com.example.pb_test_task.processing.OrderProcessor;
import com.example.pb_test_task.provider.ProviderException;
import com.example.pb_test_task.provider.ResilientProviderClient;
import com.example.pb_test_task.repository.OrderRepository;
import com.example.pb_test_task.service.OrderTransitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static com.example.pb_test_task.domain.OrderStatus.COMPLETED;
import static com.example.pb_test_task.domain.OrderStatus.FAILED;
import static com.example.pb_test_task.domain.OrderStatus.NEW;
import static com.example.pb_test_task.domain.OrderStatus.PROCESSING;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderProcessorTest {

    private static final BigDecimal AMOUNT = new BigDecimal("500.00");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderTransitionService transitionService;

    @Mock
    private ResilientProviderClient providerClient;

    @InjectMocks
    private OrderProcessor processor;

    private Order order;

    @BeforeEach
    void createOrder() {
        order = Order.create(randomUUID(), AMOUNT, LocalDate.now(), Instant.now());
    }

    @Test
    void anOrderThatCannotBeClaimedIsSkippedWithoutCallingTheProvider() {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(transitionService.transition(order, NEW, PROCESSING, null)).thenReturn(false);

        processor.process(order.getId());

        verify(providerClient, never()).charge(any(), any(), any());
    }

    @Test
    void anOrderThatNoLongerExistsIsSkipped() {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.empty());

        processor.process(order.getId());

        verify(providerClient, never()).charge(any(), any(), any());
    }

    @Test
    void aSuccessfulChargeIsSettledAsCompletedWithTheProviderReference() {
        claimSucceeds();
        when(providerClient.charge(order.getId(), order.getClientId(), AMOUNT)).thenReturn("TXN-1");
        when(transitionService.transition(order, PROCESSING, COMPLETED, "TXN-1")).thenReturn(true);

        processor.process(order.getId());

        verify(transitionService).transition(order, PROCESSING, COMPLETED, "TXN-1");
    }

    @Test
    void aProviderFailureIsSettledAsFailed() {
        claimSucceeds();
        when(providerClient.charge(order.getId(), order.getClientId(), AMOUNT))
                .thenThrow(new ProviderException.Timeout("boom"));
        when(transitionService.transition(eq(order), eq(PROCESSING), eq(FAILED), any())).thenReturn(true);

        processor.process(order.getId());

        verify(transitionService).transition(eq(order), eq(PROCESSING), eq(FAILED), any());
    }

    @Test
    void aChargeThatLandsAfterTheOrderLeftProcessingDoesNotBlowUp() {
        claimSucceeds();
        when(providerClient.charge(order.getId(), order.getClientId(), AMOUNT)).thenReturn("TXN-2");
        when(transitionService.transition(order, PROCESSING, COMPLETED, "TXN-2")).thenReturn(false);

        assertThatCode(() -> processor.process(order.getId()))
                .as("the money left the building; the operator is told, the consumer still acks")
                .doesNotThrowAnyException();
    }

    @Test
    void aFailureThatLandsAfterTheOrderLeftProcessingDoesNotBlowUp() {
        claimSucceeds();
        when(providerClient.charge(order.getId(), order.getClientId(), AMOUNT))
                .thenThrow(new ProviderException.ServerError("boom"));
        when(transitionService.transition(eq(order), eq(PROCESSING), eq(FAILED), any())).thenReturn(false);

        assertThatCode(() -> processor.process(order.getId())).doesNotThrowAnyException();
    }

    private void claimSucceeds() {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(transitionService.transition(order, NEW, PROCESSING, null)).thenReturn(true);
    }
}
