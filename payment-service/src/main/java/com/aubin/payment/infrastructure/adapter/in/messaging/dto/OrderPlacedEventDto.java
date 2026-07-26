package com.aubin.payment.infrastructure.adapter.in.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedEventDto(
        UUID eventId,
        Instant occurredOn,
        OrderIdDto orderId,
        String customerId,
        String sellerId,
        MoneyDto total
) {

    // Nested to mirror the wire format: orderId arrives as {"value": "..."}, so a flat String would not bind
    public record OrderIdDto(String value) {}

    // Deliberately not commons Money: its constructor normalizes scale, which would rewrite the amount during deserialization
    public record MoneyDto(BigDecimal amount, String currency) {}
}
