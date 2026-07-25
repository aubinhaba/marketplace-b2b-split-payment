package com.aubin.order.domain.model;

// An enum rather than a sealed interface: JPA maps it natively, a sealed hierarchy would need a converter
public enum OrderStatus {

    PLACED,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED;

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PLACED -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == SHIPPED || next == CANCELLED;
            case SHIPPED -> next == DELIVERED || next == CANCELLED;
            case DELIVERED -> next == REFUNDED;
            case CANCELLED, REFUNDED -> false;
        };
    }

    public boolean isTerminal() {
        return this == CANCELLED || this == REFUNDED || this == DELIVERED;
    }
}
