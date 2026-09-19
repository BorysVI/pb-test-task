package com.example.pb_test_task.api;

import com.example.pb_test_task.service.InconsistentStateException;
import com.example.pb_test_task.service.OrderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderException.DailyLimitExceeded.class)
    public ProblemDetail onDailyLimitExceeded(OrderException.DailyLimitExceeded e) {
        return problem(CONFLICT, "daily-limit-exceeded", "Daily limit exceeded", e.getMessage());
    }

    @ExceptionHandler(OrderException.IdempotencyKeyReused.class)
    public ProblemDetail onIdempotencyKeyReused(OrderException.IdempotencyKeyReused e) {
        return problem(UNPROCESSABLE_CONTENT, "idempotency-key-reused",
                "Idempotency key reused", e.getMessage());
    }

    @ExceptionHandler(OrderException.OrderNotFound.class)
    public ProblemDetail onOrderNotFound(OrderException.OrderNotFound e) {
        return problem(NOT_FOUND, "order-not-found", "Order not found", e.getMessage());
    }

    @ExceptionHandler(OrderException.OrderNotCancellable.class)
    public ProblemDetail onOrderNotCancellable(OrderException.OrderNotCancellable e) {
        return problem(CONFLICT, "order-not-cancellable", "Order not cancellable", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidationFailure(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(BAD_REQUEST, "validation-failed", "Invalid request", detail);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail onMissingHeader(MissingRequestHeaderException e) {
        return problem(BAD_REQUEST, "missing-header", "Missing required header", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException e) {
        Class<?> requiredType = e.getRequiredType();
        String detail = requiredType == null
                ? "%s has an invalid value".formatted(e.getName())
                : "%s is not a valid %s".formatted(e.getName(), requiredType.getSimpleName());

        return problem(BAD_REQUEST, "invalid-argument", "Invalid argument", detail);
    }

    @ExceptionHandler(InconsistentStateException.class)
    public ProblemDetail onInconsistentState(InconsistentStateException e) {
        log.error("Inconsistent state: {}", e.getMessage(), e);
        return problem(INTERNAL_SERVER_ERROR, "inconsistent-state", "Internal error",
                "Unexpected error while processing the request");
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:problem:" + type));
        problem.setTitle(title);
        return problem;
    }
}
