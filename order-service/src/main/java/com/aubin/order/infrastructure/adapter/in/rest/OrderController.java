package com.aubin.order.infrastructure.adapter.in.rest;

import com.aubin.order.application.port.in.GetOrderQuery;
import com.aubin.order.application.port.in.PlaceOrderUseCase;
import com.aubin.order.application.port.in.PlaceOrderUseCase.PlaceOrderResult;
import com.aubin.order.domain.model.Order;
import com.aubin.order.infrastructure.adapter.in.rest.dto.CreateOrderRequest;
import com.aubin.order.infrastructure.adapter.in.rest.dto.OrderCreatedResponse;
import com.aubin.order.infrastructure.adapter.in.rest.dto.OrderResponse;
import com.aubin.order.infrastructure.adapter.in.rest.mapper.OrderApiMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

// Depends on the inbound ports, not the service: keeps the controller testable under @WebMvcTest
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final GetOrderQuery getOrderQuery;
    private final OrderApiMapper mapper;

    public OrderController(PlaceOrderUseCase placeOrderUseCase,
                           GetOrderQuery getOrderQuery,
                           OrderApiMapper mapper) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.getOrderQuery = getOrderQuery;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<OrderCreatedResponse> placeOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        PlaceOrderResult result = placeOrderUseCase.placeOrder(mapper.toCommand(request));
        URI location = URI.create("/api/v1/orders/" + result.orderId());

        return ResponseEntity
                .created(location)
                .body(new OrderCreatedResponse(result.orderId()));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getById(@PathVariable String orderId) {
        Order order = getOrderQuery.getById(orderId);
        return ResponseEntity.ok(mapper.toResponse(order));
    }
}
