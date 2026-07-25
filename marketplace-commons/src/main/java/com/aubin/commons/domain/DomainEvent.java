package com.aubin.commons.domain;

import java.time.Instant;
import java.util.UUID;

// An interface rather than an abstract class because records cannot extend one
public interface DomainEvent {

    // Idempotency key: consumers check it before processing, since SQS delivers at least once
    UUID eventId();

    Instant occurredOn();

    String eventType();

    /**
     * Concrete events must override this. The default returns the event id: a plausible-looking but wrong
     * aggregate id, written straight to {@code outbox.aggregate_id}, breaking per-aggregate correlation
     * and FIFO ordering with no compile error and no failing test.
     */
    default String aggregateId() {
        return eventId().toString();
    }
}
