package com.aubin.order.application.service;

import com.aubin.commons.domain.DomainEvent;
import com.aubin.order.application.port.in.GetOrderQuery;
import com.aubin.order.application.port.in.PlaceOrderUseCase;
import com.aubin.order.application.port.out.OrderEventPublisher;
import com.aubin.order.application.port.out.OrderRepository;
import com.aubin.order.domain.exception.OrderNotFoundException;
import com.aubin.order.domain.model.Order;
import com.aubin.order.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlaceOrderService implements PlaceOrderUseCase, GetOrderQuery {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public PlaceOrderService(OrderRepository orderRepository, OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    // One transaction covers the order insert and the outbox insert: neither commits without the other
    @Override
    @Transactional
    public PlaceOrderResult placeOrder(PlaceOrderCommand command) {
        Order order = Order.place(
                command.customerId(),
                command.sellerId(),
                command.lines(),
                command.currency()
        );

        // Read the events before clearing them, or publish() silently receives an empty list
        List<DomainEvent> events = order.domainEvents();

        Order savedOrder = orderRepository.save(order.clearDomainEvents());
        eventPublisher.publish(events);

        return new PlaceOrderResult(savedOrder.id().value());
    }

    @Override
    @Transactional(readOnly = true)
    public Order getById(String orderId) {
        OrderId id = OrderId.from(orderId);
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
