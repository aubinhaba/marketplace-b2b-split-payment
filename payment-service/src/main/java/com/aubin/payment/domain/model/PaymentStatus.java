package com.aubin.payment.domain.model;

public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    REFUNDED;

    public boolean isTerminal() {
        return this == CAPTURED || this == FAILED || this == REFUNDED;
    }

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case PENDING    -> next == AUTHORIZED || next == FAILED;
            case AUTHORIZED -> next == CAPTURED || next == REFUNDED;
            case CAPTURED   -> next == REFUNDED;
            case FAILED, REFUNDED -> false;
        };
    }
}
