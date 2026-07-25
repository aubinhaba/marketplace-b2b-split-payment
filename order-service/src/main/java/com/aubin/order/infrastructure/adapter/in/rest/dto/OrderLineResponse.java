package com.aubin.order.infrastructure.adapter.in.rest.dto;

import java.math.BigDecimal;

public record OrderLineResponse(
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        String currency,
        BigDecimal lineTotal
) {}
