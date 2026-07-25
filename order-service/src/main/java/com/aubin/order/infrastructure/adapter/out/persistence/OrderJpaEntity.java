package com.aubin.order.infrastructure.adapter.out.persistence;

import com.aubin.order.domain.model.OrderStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Separate from the domain Order: @Entity on the aggregate would fail the ArchUnit boundary check (see ADR-001)
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class OrderJpaEntity {

    // No @GeneratedValue: the id comes from the domain, so no round-trip is needed
    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(name = "seller_id", nullable = false, length = 36)
    private String sellerId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    // STRING not ORDINAL: reordering the enum would silently corrupt existing rows
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    // @ElementCollection because lines are Value Objects; EAGER because an order is never read without them
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "order_lines",
            joinColumns = @JoinColumn(name = "order_id")
    )
    private List<OrderLineEmbeddable> lines = new ArrayList<>();
}
