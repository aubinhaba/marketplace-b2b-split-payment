package com.aubin.order.domain.exception;

// Extends RuntimeException, not the shared BusinessException, which imports HttpStatus (see ADR-001)
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderId) {
        super("Order not found: id=" + orderId);
    }
}
