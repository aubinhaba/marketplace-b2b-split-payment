package com.aubin.order.application.service;

import com.aubin.commons.domain.DomainEvent;
import com.aubin.commons.domain.Money;
import com.aubin.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.aubin.order.application.port.in.PlaceOrderUseCase.PlaceOrderResult;
import com.aubin.order.application.port.out.OrderEventPublisher;
import com.aubin.order.application.port.out.OrderRepository;
import com.aubin.order.domain.exception.OrderNotFoundException;
import com.aubin.order.domain.model.Order;
import com.aubin.order.domain.model.OrderId;
import com.aubin.order.domain.model.OrderLine;
import com.aubin.order.domain.model.OrderPlacedEvent;
import com.aubin.order.domain.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaceOrderService — use case orchestration")
class PlaceOrderServiceTest {

    private static final String CUSTOMER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SELLER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String CURRENCY = "EUR";

    private static PlaceOrderCommand validCommand() {
        List<OrderLine> lines = List.of(
                new OrderLine("prod-1", "Product 1", 2, Money.of("10.00", CURRENCY))
        );
        return new PlaceOrderCommand(CUSTOMER_ID, SELLER_ID, lines, CURRENCY);
    }

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher eventPublisher;

    private PlaceOrderService service;

    @BeforeEach
    void setUp() {
        service = new PlaceOrderService(orderRepository, eventPublisher);
    }

    @Nested
    @DisplayName("placeOrder() — nominal path")
    class HappyPath {

        @BeforeEach
        void repositoryEchoesItsArgument() {
            when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        @DisplayName("persists the order with its domain events cleared")
        void placeOrder_savesOrderWithoutEvents() {
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            when(orderRepository.save(orderCaptor.capture()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.placeOrder(validCommand());

            assertThat(orderCaptor.getValue().domainEvents())
                    .as("events must be cleared before persistence")
                    .isEmpty();
        }

        @Test
        @DisplayName("publishes exactly one OrderPlacedEvent carrying the order data")
        void placeOrder_publishesOrderPlacedEvent() {
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<DomainEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
            doNothing().when(eventPublisher).publish(eventsCaptor.capture());

            service.placeOrder(validCommand());

            List<DomainEvent> publishedEvents = eventsCaptor.getValue();
            assertThat(publishedEvents).hasSize(1);
            assertThat(publishedEvents.get(0)).isInstanceOf(OrderPlacedEvent.class);

            OrderPlacedEvent event = (OrderPlacedEvent) publishedEvents.get(0);
            assertThat(event.customerId()).isEqualTo(CUSTOMER_ID);
            assertThat(event.sellerId()).isEqualTo(SELLER_ID);
            assertThat(event.total()).isEqualTo(Money.of("20.00", CURRENCY));
        }

        @Test
        @DisplayName("returns a result holding a valid UUID")
        void placeOrder_returnsResultWithUuid() {
            PlaceOrderResult result = service.placeOrder(validCommand());

            assertThat(result.orderId()).isNotBlank();
            assertThatCode(() -> UUID.fromString(result.orderId())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("saves before publishing")
        void placeOrder_savesBeforePublishing() {
            var inOrder = inOrder(orderRepository, eventPublisher);

            service.placeOrder(validCommand());

            inOrder.verify(orderRepository).save(any());
            inOrder.verify(eventPublisher).publish(any());
        }
    }

    @Nested
    @DisplayName("placeOrder() — failure handling")
    class FailureHandling {

        @Test
        @DisplayName("does not publish when the repository fails")
        void placeOrder_doesNotPublishWhenSaveFails() {
            when(orderRepository.save(any())).thenThrow(new RuntimeException("simulated database outage"));

            assertThatThrownBy(() -> service.placeOrder(validCommand()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("simulated database outage");

            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("propagates a publisher failure so the transaction can roll back")
        void placeOrder_propagatesPublisherFailure() {
            when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            doThrow(new RuntimeException("outbox unavailable")).when(eventPublisher).publish(any());

            assertThatThrownBy(() -> service.placeOrder(validCommand()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("outbox unavailable");
        }
    }

    @Nested
    @DisplayName("placeOrder() — domain error propagation")
    class DomainErrorPropagation {

        @Test
        @DisplayName("propagates the invariant failure for an empty line list")
        void placeOrder_propagatesEmptyLines() {
            PlaceOrderCommand invalid = new PlaceOrderCommand(CUSTOMER_ID, SELLER_ID, List.of(), CURRENCY);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> service.placeOrder(invalid))
                    .withMessageContaining("at least one line");

            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("propagates the invariant failure for self-purchase")
        void placeOrder_propagatesSelfPurchase() {
            PlaceOrderCommand invalid = new PlaceOrderCommand(
                    CUSTOMER_ID, CUSTOMER_ID,
                    List.of(new OrderLine("p", "Product", 1, Money.of("5.00", CURRENCY))),
                    CURRENCY
            );

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> service.placeOrder(invalid))
                    .withMessageContaining("cannot order their own products");

            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetById {

        private final String validOrderId = UUID.randomUUID().toString();

        private Order existingOrder() {
            return Order.reconstitute(
                    OrderId.from(validOrderId),
                    CUSTOMER_ID,
                    SELLER_ID,
                    List.of(new OrderLine("prod-1", "Product", 1, Money.of("15.00", CURRENCY))),
                    Money.of("15.00", CURRENCY),
                    OrderStatus.PLACED,
                    Instant.now()
            );
        }

        @Test
        @DisplayName("returns the order when it exists")
        void getById_returnsExistingOrder() {
            when(orderRepository.findById(OrderId.from(validOrderId)))
                    .thenReturn(Optional.of(existingOrder()));

            Order result = service.getById(validOrderId);

            assertThat(result.id().value()).isEqualTo(validOrderId);
            assertThat(result.customerId()).isEqualTo(CUSTOMER_ID);
        }

        @Test
        @DisplayName("throws OrderNotFoundException when it does not exist")
        void getById_throwsWhenMissing() {
            when(orderRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(validOrderId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining(validOrderId);
        }

        @Test
        @DisplayName("rejects a malformed id before hitting the repository")
        void getById_throwsOnMalformedId() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> service.getById("not-a-uuid"));

            verify(orderRepository, never()).findById(any());
        }
    }
}
