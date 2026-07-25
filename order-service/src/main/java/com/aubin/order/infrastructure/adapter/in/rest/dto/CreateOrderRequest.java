package com.aubin.order.infrastructure.adapter.in.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(

        @NotBlank(message = "customerId is required")
        String customerId,

        @NotBlank(message = "sellerId is required")
        String sellerId,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        // @Valid is required here or the per-line constraints are silently skipped
        @NotEmpty(message = "An order must contain at least one line")
        @Valid
        List<OrderLineRequest> lines
) {}
