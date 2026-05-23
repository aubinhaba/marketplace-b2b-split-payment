package com.aubin.payment.infrastructure;

import com.aubin.payment.TestcontainersConfiguration;
import com.aubin.payment.domain.model.Payment;
import com.aubin.payment.infrastructure.persistence.PaymentPersistenceAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, PaymentPersistenceAdapter.class})
@TestPropertySource(properties = "spring.flyway.enabled=true")
class PaymentPersistenceAdapterIT {

    @Autowired
    private PaymentPersistenceAdapter adapter;

    @Test
    void save_and_findById_roundtrip() {
        var payment = Payment.create("customer-1", BigDecimal.TEN, "EUR");

        var saved = adapter.save(payment);
        var found = adapter.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().customerId()).isEqualTo("customer-1");
        assertThat(found.get().amount()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void findById_returns_empty_when_not_found() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }
}
