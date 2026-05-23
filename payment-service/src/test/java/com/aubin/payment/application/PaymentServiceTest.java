package com.aubin.payment.application;

import com.aubin.payment.application.service.PaymentService;
import com.aubin.payment.domain.exception.PaymentNotFoundException;
import com.aubin.payment.domain.model.Payment;
import com.aubin.payment.domain.model.PaymentStatus;
import com.aubin.payment.application.port.in.ProcessPaymentUseCase.ProcessPaymentCommand;
import com.aubin.payment.application.port.out.PaymentRepository;
import com.aubin.payment.application.port.out.PspGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PspGateway pspGateway;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, pspGateway);
    }

    @Test
    void process_saves_authorized_payment() {
        var command = new ProcessPaymentCommand("customer-1", BigDecimal.TEN, "EUR");
        var pending = Payment.create("customer-1", BigDecimal.TEN, "EUR");
        var authorized = pending.authorize();

        when(paymentRepository.save(any())).thenReturn(pending).thenReturn(authorized);
        when(pspGateway.authorize(any())).thenReturn(authorized);

        var result = paymentService.process(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    void getById_throws_when_payment_not_found() {
        var id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getById(id))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
