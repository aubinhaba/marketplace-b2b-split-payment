package com.aubin.order.infrastructure.adapter.in.rest;

import com.aubin.commons.domain.Money;
import com.aubin.order.application.port.in.GetOrderQuery;
import com.aubin.order.application.port.in.PlaceOrderUseCase;
import com.aubin.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.aubin.order.application.port.in.PlaceOrderUseCase.PlaceOrderResult;
import com.aubin.order.domain.exception.OrderNotFoundException;
import com.aubin.order.domain.model.Order;
import com.aubin.order.domain.model.OrderId;
import com.aubin.order.domain.model.OrderLine;
import com.aubin.order.domain.model.OrderStatus;
import com.aubin.order.infrastructure.adapter.in.rest.dto.OrderLineResponse;
import com.aubin.order.infrastructure.adapter.in.rest.dto.OrderResponse;
import com.aubin.order.infrastructure.adapter.in.rest.mapper.OrderApiMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@DisplayName("OrderController — REST layer")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceOrderUseCase placeOrderUseCase;

    @MockitoBean
    private GetOrderQuery getOrderQuery;

    @MockitoBean
    private OrderApiMapper mapper;

    private static final String CUSTOMER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SELLER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String ORDER_ID = UUID.randomUUID().toString();

    private static final String VALID_POST_BODY = """
            {
              "customerId": "%s",
              "sellerId":   "%s",
              "currency":   "EUR",
              "lines": [
                {
                  "productId":   "prod-abc",
                  "productName": "Mechanical keyboard",
                  "quantity":    2,
                  "unitPrice":   89.99
                }
              ]
            }
            """.formatted(CUSTOMER_ID, SELLER_ID);

    @Nested
    @DisplayName("POST /api/v1/orders")
    class PostOrders {

        @Test
        @DisplayName("returns 201 with a Location header and the new id")
        void placeOrder_returns201WithLocation() throws Exception {
            when(mapper.toCommand(any())).thenReturn(
                    new PlaceOrderCommand(CUSTOMER_ID, SELLER_ID, List.of(), "EUR")
            );
            when(placeOrderUseCase.placeOrder(any())).thenReturn(new PlaceOrderResult(ORDER_ID));

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_POST_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/orders/" + ORDER_ID))
                    .andExpect(jsonPath("$.orderId").value(ORDER_ID));
        }

        @Test
        @DisplayName("returns 400 when the body fails validation")
        void placeOrder_returns400OnEmptyBody() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 as ProblemDetail when a domain invariant is violated")
        void placeOrder_returns400OnInvariantViolation() throws Exception {
            when(mapper.toCommand(any())).thenReturn(
                    new PlaceOrderCommand(CUSTOMER_ID, SELLER_ID, List.of(), "EUR")
            );
            when(placeOrderUseCase.placeOrder(any()))
                    .thenThrow(new IllegalArgumentException("A seller cannot order their own products"));

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_POST_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid Request"))
                    .andExpect(jsonPath("$.detail").value("A seller cannot order their own products"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{orderId}")
    class GetOrderById {

        @Test
        @DisplayName("returns 200 with the full order representation")
        void getById_returns200() throws Exception {
            Order order = Order.reconstitute(
                    OrderId.from(ORDER_ID),
                    CUSTOMER_ID,
                    SELLER_ID,
                    List.of(new OrderLine("prod-abc", "Keyboard", 1, Money.of("89.99", "EUR"))),
                    Money.of("89.99", "EUR"),
                    OrderStatus.PLACED,
                    Instant.now()
            );

            when(getOrderQuery.getById(ORDER_ID)).thenReturn(order);
            when(mapper.toResponse(any())).thenReturn(
                    new OrderResponse(
                            ORDER_ID,
                            CUSTOMER_ID,
                            SELLER_ID,
                            List.of(new OrderLineResponse("prod-abc", "Keyboard", 1,
                                    new BigDecimal("89.99"), "EUR", new BigDecimal("89.99"))),
                            new BigDecimal("89.99"),
                            "EUR",
                            "PLACED",
                            Instant.now()
                    )
            );

            mockMvc.perform(get("/api/v1/orders/" + ORDER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(ORDER_ID))
                    .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID))
                    .andExpect(jsonPath("$.status").value("PLACED"))
                    .andExpect(jsonPath("$.lines").isArray())
                    .andExpect(jsonPath("$.lines[0].productId").value("prod-abc"));
        }

        @Test
        @DisplayName("returns 404 as ProblemDetail when the order is unknown")
        void getById_returns404() throws Exception {
            when(getOrderQuery.getById(ORDER_ID)).thenThrow(new OrderNotFoundException(ORDER_ID));

            mockMvc.perform(get("/api/v1/orders/" + ORDER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Order Not Found"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("returns 400 when the id is not a valid UUID")
        void getById_returns400OnMalformedId() throws Exception {
            when(getOrderQuery.getById("not-a-uuid"))
                    .thenThrow(new IllegalArgumentException("Invalid UUID string: not-a-uuid"));

            mockMvc.perform(get("/api/v1/orders/not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid Request"));
        }
    }
}
