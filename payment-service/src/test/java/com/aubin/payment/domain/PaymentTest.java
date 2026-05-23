package com.aubin.payment.domain;

import com.aubin.payment.domain.model.Payment;
import com.aubin.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    @Test
    void create_initializes_with_pending_status() {
        var payment = Payment.create("customer-1", BigDecimal.TEN, "EUR");

        assertThat(payment.id()).isNotNull();
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.createdAt()).isNotNull();
    }

    @Test
    void authorize_transitions_status_to_authorized() {
        var payment = Payment.create("customer-1", BigDecimal.TEN, "EUR");

        var authorized = payment.authorize();

        assertThat(authorized.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(authorized.id()).isEqualTo(payment.id());
    }

    @Test
    void fail_transitions_status_to_failed() {
        var payment = Payment.create("customer-1", BigDecimal.TEN, "EUR");

        var failed = payment.fail();

        assertThat(failed.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void status_canTransitionTo_enforces_valid_paths() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.AUTHORIZED)).isTrue();
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.CAPTURED)).isFalse();
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.AUTHORIZED)).isFalse();
    }
}
