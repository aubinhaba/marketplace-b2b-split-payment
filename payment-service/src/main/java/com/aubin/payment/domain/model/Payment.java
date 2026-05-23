package com.aubin.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(
        UUID id,
        String customerId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        Instant createdAt
) {
    public static Payment create(String customerId, BigDecimal amount, String currency) {
        return new Payment(UUID.randomUUID(), customerId, amount, currency, PaymentStatus.PENDING, Instant.now());
    }

    public Payment authorize() {
        return new Payment(id, customerId, amount, currency, PaymentStatus.AUTHORIZED, createdAt);
    }

    public Payment fail() {
        return new Payment(id, customerId, amount, currency, PaymentStatus.FAILED, createdAt);
    }
}
