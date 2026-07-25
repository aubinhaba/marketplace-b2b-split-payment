package com.aubin.order.application.port.in;

import com.aubin.order.domain.model.OrderLine;

import java.util.List;

// Command and result nested here: a new input enriches the record instead of changing the signature
public interface PlaceOrderUseCase {

    PlaceOrderResult placeOrder(PlaceOrderCommand command);

    // A pure carrier: the business invariants are validated by Order.place, not here
    record PlaceOrderCommand(
            String customerId,
            String sellerId,
            List<OrderLine> lines,
            String currency
    ) {}

    record PlaceOrderResult(String orderId) {}
}
