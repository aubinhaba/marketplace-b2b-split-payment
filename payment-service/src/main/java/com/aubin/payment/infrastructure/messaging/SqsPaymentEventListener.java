package com.aubin.payment.infrastructure.messaging;

import org.springframework.stereotype.Component;

@Component
public class SqsPaymentEventListener {

    public void onPaymentEvent(String message) {
    }
}
