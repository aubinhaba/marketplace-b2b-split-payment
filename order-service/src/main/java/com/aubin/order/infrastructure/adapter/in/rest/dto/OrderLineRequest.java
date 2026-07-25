package com.aubin.order.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderLineRequest(

        @NotBlank(message = "productId is required")
        String productId,

        @NotBlank(message = "productName is required")
        String productName,

        @Min(value = 1, message = "quantity must be at least 1")
        int quantity,

        @NotNull(message = "unitPrice is required")
        @Positive(message = "unitPrice must be strictly positive")
        BigDecimal unitPrice
) {}
