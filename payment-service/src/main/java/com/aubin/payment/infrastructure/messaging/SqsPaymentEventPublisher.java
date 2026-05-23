package com.aubin.payment.infrastructure.messaging;

import com.aubin.payment.domain.model.Payment;
import org.springframework.stereotype.Component;

@Component
public class SqsPaymentEventPublisher {

    public void publishPaymentProcessed(Payment payment) {
    }
}
