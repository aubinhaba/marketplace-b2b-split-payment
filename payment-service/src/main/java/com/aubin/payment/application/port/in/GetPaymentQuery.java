package com.aubin.payment.application.port.in;

import com.aubin.payment.domain.model.Payment;

import java.util.UUID;

public interface GetPaymentQuery {

    Payment getById(UUID paymentId);
}
