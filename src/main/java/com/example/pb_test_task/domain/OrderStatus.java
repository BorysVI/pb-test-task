package com.example.pb_test_task.domain;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    NEW,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Set<OrderStatus> TERMINAL = EnumSet.of(COMPLETED, FAILED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
