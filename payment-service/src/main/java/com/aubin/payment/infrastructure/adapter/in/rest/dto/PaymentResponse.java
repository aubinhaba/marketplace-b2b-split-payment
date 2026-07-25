package com.aubin.payment.infrastructure.adapter.in.rest.dto;

import com.aubin.payment.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String customerId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        Instant createdAt
) {}
