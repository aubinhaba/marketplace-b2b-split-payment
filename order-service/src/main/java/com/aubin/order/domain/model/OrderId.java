package com.aubin.order.domain.model;

import com.aubin.commons.domain.AggregateId;

import java.util.Objects;
import java.util.UUID;

public record OrderId(String value) implements AggregateId {

    public OrderId {
        Objects.requireNonNull(value, "value required");
        UUID.fromString(value);
    }

    public static OrderId generate() {
        return new OrderId(UUID.randomUUID().toString());
    }

    public static OrderId from(String value) {
        return new OrderId(value);
    }
}
