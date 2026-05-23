package com.aubin.payment.application.port.in;

import com.aubin.payment.domain.model.Payment;

import java.math.BigDecimal;

public interface ProcessPaymentUseCase {

    Payment process(ProcessPaymentCommand command);

    record ProcessPaymentCommand(
            String customerId,
            BigDecimal amount,
            String currency
    ) {}
}
