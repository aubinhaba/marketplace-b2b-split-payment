package com.aubin.payment.domain.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException{
    public PaymentNotFoundException(UUID paymentId) {
        super("Payment not found with id: " + paymentId);
    }
}
