package com.aubin.payment.infrastructure.persistence;

import com.aubin.payment.domain.model.PaymentStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
class PaymentJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentJpaEntity() {}

    PaymentJpaEntity(UUID id, String customerId, BigDecimal amount, String currency,
                     PaymentStatus status, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    UUID getId() { return id; }
    String getCustomerId() { return customerId; }
    BigDecimal getAmount() { return amount; }
    String getCurrency() { return currency; }
    PaymentStatus getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }
}
