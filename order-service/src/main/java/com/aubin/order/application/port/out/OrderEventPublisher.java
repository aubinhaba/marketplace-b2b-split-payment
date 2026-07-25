package com.aubin.order.application.port.out;

import com.aubin.commons.domain.DomainEvent;

import java.util.List;

public interface OrderEventPublisher {

    // Runs in the caller's transaction: if it throws, neither the order nor its events are persisted
    void publish(List<DomainEvent> events);
}
