package com.aubin.payment.infrastructure.adapter.in.rest;

import com.aubin.payment.application.port.in.GetPaymentQuery;
import com.aubin.payment.application.port.in.ProcessPaymentUseCase;
import com.aubin.payment.infrastructure.adapter.in.rest.dto.CreatePaymentRequest;
import com.aubin.payment.infrastructure.adapter.in.rest.dto.PaymentResponse;
import com.aubin.payment.infrastructure.adapter.in.rest.mapper.PaymentApiMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final ProcessPaymentUseCase processPayment;
    private final GetPaymentQuery getPayment;
    private final PaymentApiMapper mapper;

    public PaymentController(ProcessPaymentUseCase processPayment,
                             GetPaymentQuery getPayment,
                             PaymentApiMapper mapper) {
        this.processPayment = processPayment;
        this.getPayment = getPayment;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request) {
        return mapper.toResponse(
                processPayment.process(mapper.toCommand(request))
        );
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(getPayment.getById(id));
    }
}
