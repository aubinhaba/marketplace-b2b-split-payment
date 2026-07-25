package com.aubin.order.domain.model;

import com.aubin.commons.domain.DomainEvent;
import com.aubin.commons.domain.Money;

import java.time.Instant;
import java.util.UUID;

// Two constructors: the canonical one for Jackson, the business one so callers cannot forge eventId or occurredOn
public record OrderPlacedEvent(
        UUID eventId,
        Instant occurredOn,
        OrderId orderId,
        String customerId,
        String sellerId,
        Money total
) implements DomainEvent {

    public OrderPlacedEvent(OrderId orderId, String customerId, String sellerId, Money total) {
        this(UUID.randomUUID(), Instant.now(), orderId, customerId, sellerId, total);
    }

    @Override
    public String eventType() {
        return "order.placed";
    }

    @Override
    public String aggregateId() {
        return orderId().value();
    }
}
