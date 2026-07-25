package com.aubin.order.domain.model;

import com.aubin.commons.domain.DomainEvent;
import com.aubin.commons.domain.Money;

import java.time.Instant;
import java.util.List;

public final class Order {

    private final OrderId id;
    private final String customerId;
    private final String sellerId;
    private final List<OrderLine> lines;
    private final Money total;
    private final OrderStatus status;
    private final Instant placedAt;
    private final List<DomainEvent> domainEvents;

    private Order(OrderId id, String customerId, String sellerId,
                  List<OrderLine> lines, Money total, OrderStatus status,
                  Instant placedAt, List<DomainEvent> domainEvents) {
        this.id = id;
        this.customerId = customerId;
        this.sellerId = sellerId;
        this.lines = List.copyOf(lines);
        this.total = total;
        this.status = status;
        this.placedAt = placedAt;
        this.domainEvents = List.copyOf(domainEvents);
    }

    // IllegalArgumentException not BusinessException: the latter carries an HttpStatus and would drag Spring into the domain
    public static Order place(String customerId, String sellerId,
                              List<OrderLine> lines, String currency) {
        requireText(customerId, "customerId");
        requireText(sellerId, "sellerId");

        if (customerId.equals(sellerId)) {
            throw new IllegalArgumentException(
                    "A seller cannot order their own products: customerId == sellerId == " + customerId);
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one line");
        }

        // Money.add rejects mixed currencies, so summing the lines also proves they agree
        Money total = lines.stream()
                .map(OrderLine::lineTotal)
                .reduce(Money.zero(currency), Money::add);

        if (total.isZeroOrNegative()) {
            throw new IllegalArgumentException("Order total must be strictly positive, got " + total);
        }

        OrderId orderId = OrderId.generate();
        OrderPlacedEvent event = new OrderPlacedEvent(orderId, customerId, sellerId, total);

        return new Order(orderId, customerId, sellerId, lines, total,
                OrderStatus.PLACED, Instant.now(), List.of(event));
    }

    // No event on reconstitute: the fact already happened, re-emitting it would duplicate outbox rows
    public static Order reconstitute(OrderId id, String customerId, String sellerId,
                                     List<OrderLine> lines, Money total, OrderStatus status,
                                     Instant placedAt) {
        return new Order(id, customerId, sellerId, lines, total, status, placedAt, List.of());
    }

    public Order clearDomainEvents() {
        return new Order(id, customerId, sellerId, lines, total, status, placedAt, List.of());
    }

    public OrderId id() {
        return id;
    }

    public String customerId() {
        return customerId;
    }

    public String sellerId() {
        return sellerId;
    }

    public List<OrderLine> lines() {
        return lines;
    }

    public Money total() {
        return total;
    }

    public OrderStatus status() {
        return status;
    }

    public Instant placedAt() {
        return placedAt;
    }

    public List<DomainEvent> domainEvents() {
        return domainEvents;
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " required");
        }
    }
}
