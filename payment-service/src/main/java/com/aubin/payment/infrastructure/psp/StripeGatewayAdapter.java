package com.aubin.payment.infrastructure.psp;

import com.aubin.payment.domain.model.Payment;
import com.aubin.payment.application.port.out.PspGateway;
import org.springframework.stereotype.Component;

@Component
public class StripeGatewayAdapter implements PspGateway {

    @Override
    public Payment authorize(Payment payment) {
        return payment.authorize();
    }
}
