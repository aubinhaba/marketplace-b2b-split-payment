package com.aubin.order.infrastructure.adapter.in.rest.mapper;

import com.aubin.commons.domain.Money;
import com.aubin.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.aubin.order.domain.model.Order;
import com.aubin.order.domain.model.OrderLine;
import com.aubin.order.infrastructure.adapter.in.rest.dto.CreateOrderRequest;
import com.aubin.order.infrastructure.adapter.in.rest.dto.OrderLineRequest;
import com.aubin.order.infrastructure.adapter.in.rest.dto.OrderLineResponse;
import com.aubin.order.infrastructure.adapter.in.rest.dto.OrderResponse;
import org.mapstruct.Mapper;

import java.util.List;

// default methods, not generated: a line's Money draws its currency from the enclosing request, which MapStruct cannot walk
@Mapper(componentModel = "spring")
public interface OrderApiMapper {

    default PlaceOrderCommand toCommand(CreateOrderRequest request) {
        List<OrderLine> lines = request.lines().stream()
                .map(line -> toOrderLine(line, request.currency()))
                .toList();

        return new PlaceOrderCommand(
                request.customerId(),
                request.sellerId(),
                lines,
                request.currency()
        );
    }

    default OrderLine toOrderLine(OrderLineRequest lineRequest, String currency) {
        return new OrderLine(
                lineRequest.productId(),
                lineRequest.productName(),
                lineRequest.quantity(),
                Money.of(lineRequest.unitPrice(), currency)
        );
    }

    default OrderResponse toResponse(Order order) {
        List<OrderLineResponse> lineResponses = order.lines().stream()
                .map(this::toLineResponse)
                .toList();

        return new OrderResponse(
                order.id().value(),
                order.customerId(),
                order.sellerId(),
                lineResponses,
                order.total().amount(),
                order.total().currency(),
                order.status().name(),
                order.placedAt()
        );
    }

    default OrderLineResponse toLineResponse(OrderLine line) {
        return new OrderLineResponse(
                line.productId(),
                line.productName(),
                line.quantity(),
                line.unitPrice().amount(),
                line.unitPrice().currency(),
                line.lineTotal().amount()
        );
    }
}
