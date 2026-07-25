package com.aubin.order.domain.model;

import com.aubin.commons.domain.Money;

import java.math.BigDecimal;
import java.util.Objects;

public record OrderLine(
        String productId,
        String productName,
        int quantity,
        Money unitPrice
) {

    public OrderLine {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId required");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("productName required");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1, got " + quantity);
        }
        Objects.requireNonNull(unitPrice, "unitPrice required");
        if (unitPrice.isZeroOrNegative()) {
            throw new IllegalArgumentException("Unit price must be strictly positive, got " + unitPrice);
        }
    }

    public Money lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
