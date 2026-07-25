package com.aubin.order.infrastructure.adapter.in.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderId,
        String customerId,
        String sellerId,
        List<OrderLineResponse> lines,
        BigDecimal totalAmount,
        String currency,
        String status,
        Instant placedAt
) {}
