package com.aubin.order.application.port.out;

import com.aubin.order.domain.model.Order;
import com.aubin.order.domain.model.OrderId;

import java.util.Optional;

public interface OrderRepository {

    // The order must have its domain events cleared: those belong to the outbox, not the entity
    Order save(Order order);

    Optional<Order> findById(OrderId id);
}
