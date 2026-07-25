package com.aubin.order.application.port.in;

import com.aubin.order.domain.model.Order;

public interface GetOrderQuery {

    Order getById(String orderId);
}
