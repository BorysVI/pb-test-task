package com.example.pb_test_task.event;

import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class OrderRoutingKey {

    private static final String PREFIX = "order.";

    public static final String CREATED = PREFIX + "created";
    public static final String PROCESSING_STARTED = PREFIX + "processing";
    public static final String COMPLETED = PREFIX + "completed";
    public static final String FAILED = PREFIX + "failed";
    public static final String CANCELLED = PREFIX + "cancelled";
    public static final String ANY = PREFIX + "#";
}
