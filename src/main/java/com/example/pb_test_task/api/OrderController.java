package com.example.pb_test_task.api;

import com.example.pb_test_task.api.dto.CreateOrderRequest;
import com.example.pb_test_task.api.dto.OrderResponse;
import com.example.pb_test_task.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.ResponseEntity.status;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    private final OrderService orderService;


    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestHeader(IDEMPOTENCY_HEADER) UUID idempotencyKey,
                                                @Valid @RequestBody CreateOrderRequest request) {
        OrderService.CreateResult result = orderService.create(idempotencyKey, request.clientId(), request.amount());
        return status(result.idempotentReplay() ? OK : CREATED).body(OrderResponse.of(result.order()));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.of(orderService.find(id));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id) {
        return OrderResponse.of(orderService.cancel(id));
    }
}
