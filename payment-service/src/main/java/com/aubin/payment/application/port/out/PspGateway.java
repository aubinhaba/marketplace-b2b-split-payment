package com.aubin.payment.application.port.out;

import com.aubin.payment.domain.model.Payment;

public interface PspGateway {

    Payment authorize(Payment payment);
}
