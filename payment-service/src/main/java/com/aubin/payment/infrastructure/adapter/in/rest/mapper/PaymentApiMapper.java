package com.aubin.payment.infrastructure.adapter.in.rest.mapper;

import com.aubin.payment.domain.model.Payment;
import com.aubin.payment.application.port.in.ProcessPaymentUseCase.ProcessPaymentCommand;
import com.aubin.payment.infrastructure.adapter.in.rest.dto.CreatePaymentRequest;
import com.aubin.payment.infrastructure.adapter.in.rest.dto.PaymentResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentApiMapper {

    ProcessPaymentCommand toCommand(CreatePaymentRequest request);

    PaymentResponse toResponse(Payment payment);
}
