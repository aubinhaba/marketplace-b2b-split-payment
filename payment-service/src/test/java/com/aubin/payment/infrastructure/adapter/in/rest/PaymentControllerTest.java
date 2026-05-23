package com.aubin.payment.infrastructure.adapter.in.rest;

import com.aubin.payment.domain.model.Payment;
import com.aubin.payment.domain.model.PaymentStatus;
import com.aubin.payment.application.port.in.GetPaymentQuery;
import com.aubin.payment.application.port.in.ProcessPaymentUseCase;
import com.aubin.payment.application.port.in.ProcessPaymentUseCase.ProcessPaymentCommand;
import com.aubin.payment.infrastructure.adapter.in.rest.dto.PaymentResponse;
import com.aubin.payment.infrastructure.adapter.in.rest.mapper.PaymentApiMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@WithMockUser
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcessPaymentUseCase processPayment;

    @MockitoBean
    private GetPaymentQuery getPayment;

    @MockitoBean
    private PaymentApiMapper mapper;

    @Test
    void create_returns_201_with_payment_response() throws Exception {
        var id = UUID.randomUUID();
        var payment = Payment.create("cust-1", BigDecimal.TEN, "EUR");
        var response = new PaymentResponse(id, "cust-1", BigDecimal.TEN, "EUR",
                PaymentStatus.AUTHORIZED, Instant.now());

        when(mapper.toCommand(any())).thenReturn(new ProcessPaymentCommand("cust-1", BigDecimal.TEN, "EUR"));
        when(processPayment.process(any())).thenReturn(payment);
        when(mapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "customerId": "cust-1", "amount": 10.00, "currency": "EUR" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"));
    }

    @Test
    void getById_returns_200() throws Exception {
        var id = UUID.randomUUID();
        var payment = Payment.create("cust-1", BigDecimal.TEN, "EUR");
        var response = new PaymentResponse(id, "cust-1", BigDecimal.TEN, "EUR",
                PaymentStatus.AUTHORIZED, Instant.now());

        when(getPayment.getById(id)).thenReturn(payment);
        when(mapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }
}
