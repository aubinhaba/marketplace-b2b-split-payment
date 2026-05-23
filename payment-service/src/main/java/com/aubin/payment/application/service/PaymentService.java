package com.aubin.payment.application.service;

import com.aubin.payment.domain.exception.PaymentNotFoundException;
import com.aubin.payment.domain.model.Payment;
import com.aubin.payment.application.port.in.GetPaymentQuery;
import com.aubin.payment.application.port.in.ProcessPaymentUseCase;
import com.aubin.payment.application.port.out.PaymentRepository;
import com.aubin.payment.application.port.out.PspGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService implements ProcessPaymentUseCase, GetPaymentQuery{

    private final PaymentRepository repository;
    private final PspGateway pspGateway;

    public PaymentService(PaymentRepository repository, PspGateway pspGateway) {
        this.repository = repository;
        this.pspGateway = pspGateway;
    }

    @Override
    @Transactional
    public Payment process(ProcessPaymentCommand command) {
        var payment = Payment.create(command.customerId(), command.amount(), command.currency());
        var saved = repository.save(payment);
        var authorized = pspGateway.authorize(saved);
        return repository.save(authorized);
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getById(UUID paymentId) {
        return repository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
