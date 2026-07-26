package com.aubin.payment.infrastructure.adapter.out.persistence;

import com.aubin.payment.domain.model.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
class PaymentJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 36)
    private String orderId;

    @Column(nullable = false)
    private String sellerId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    // STRING not ORDINAL: reordering the enum would silently corrupt existing rows
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentJpaEntity() {}

    PaymentJpaEntity(UUID id, String orderId, String sellerId, String customerId,
                     BigDecimal amount, String currency, PaymentStatus status, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.sellerId = sellerId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    UUID getId() { return id; }
    String getOrderId() { return orderId; }
    String getSellerId() { return sellerId; }
    String getCustomerId() { return customerId; }
    BigDecimal getAmount() { return amount; }
    String getCurrency() { return currency; }
    PaymentStatus getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }
}
