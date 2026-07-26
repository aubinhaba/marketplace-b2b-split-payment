package com.aubin.payment.application.port.out;

import java.util.UUID;

public interface IdempotencyStore {

    // One atomic call, not isProcessed() + markAsProcessed(): two calls race on concurrent redeliveries
    boolean tryMark(UUID eventId);
}
