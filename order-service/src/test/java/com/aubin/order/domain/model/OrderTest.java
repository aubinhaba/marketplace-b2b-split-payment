package com.aubin.order.domain.model;

import com.aubin.commons.domain.DomainEvent;
import com.aubin.commons.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("Order — aggregate root")
class OrderTest {

    private static final String CUSTOMER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SELLER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String CURRENCY = "EUR";

    private static OrderLine line(String productId, int quantity, String unitPrice) {
        return new OrderLine(productId, "Product " + productId, quantity, Money.of(unitPrice, CURRENCY));
    }

    @Nested
    @DisplayName("place() — valid order")
    class PlaceHappyPath {

        @Test
        @DisplayName("creates the order in PLACED status")
        void place_returnsOrderInPlacedStatus() {
            List<OrderLine> lines = List.of(line("prod-1", 2, "10.00"));

            Order order = Order.place(CUSTOMER_ID, SELLER_ID, lines, CURRENCY);

            assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        }

        @Test
        @DisplayName("assigns a generated, well-formed OrderId")
        void place_assignsGeneratedId() {
            List<OrderLine> lines = List.of(line("prod-1", 1, "5.00"));

            Order order = Order.place(CUSTOMER_ID, SELLER_ID, lines, CURRENCY);

            assertThat(order.id()).isNotNull();
            assertThatCode(() -> OrderId.from(order.id().value())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("totals the order as the sum of its line totals")
        void place_computesTotal() {
            List<OrderLine> lines = List.of(
                    line("prod-1", 2, "10.00"),
                    line("prod-2", 3, "5.00")
            );

            Order order = Order.place(CUSTOMER_ID, SELLER_ID, lines, CURRENCY);

            assertThat(order.total()).isEqualTo(Money.of("35.00", CURRENCY));
        }

        @Test
        @DisplayName("emits exactly one OrderPlacedEvent")
        void place_emitsSingleOrderPlacedEvent() {
            List<OrderLine> lines = List.of(line("prod-1", 1, "10.00"));

            Order order = Order.place(CUSTOMER_ID, SELLER_ID, lines, CURRENCY);

            assertThat(order.domainEvents()).hasSize(1);
            assertThat(order.domainEvents().get(0)).isInstanceOf(OrderPlacedEvent.class);
        }

        @Test
        @DisplayName("populates the event with the order data")
        void place_eventCarriesOrderData() {
            List<OrderLine> lines = List.of(line("prod-1", 2, "10.00"));

            Order order = Order.place(CUSTOMER_ID, SELLER_ID, lines, CURRENCY);

            OrderPlacedEvent event = (OrderPlacedEvent) order.domainEvents().get(0);
            assertThat(event.orderId()).isEqualTo(order.id());
            assertThat(event.customerId()).isEqualTo(CUSTOMER_ID);
            assertThat(event.sellerId()).isEqualTo(SELLER_ID);
            assertThat(event.total()).isEqualTo(order.total());
            assertThat(event.eventId()).isNotNull();
            assertThat(event.occurredOn()).isNotNull();
        }

        @Test
        @DisplayName("types the event as 'order.placed'")
        void place_eventTypeIsOrderPlaced() {
            List<OrderLine> lines = List.of(line("prod-1", 1, "10.00"));
            Order order = Order.place(CUSTOMER_ID, SELLER_ID, lines, CURRENCY);

            DomainEvent event = order.domainEvents().get(0);

            assertThat(event.eventType()).isEqualTo("order.placed");
        }

        @Test
        @DisplayName("keeps the lines in the order they were supplied")
        void place_preservesLineOrder() {
            List<OrderLine> lines = List.of(
                    line("prod-A", 1, "5.00"),
                    line("prod-B", 2, "3.00")
            );

            Order order = Order.place(CUSTOMER_ID, SELLER_ID, lines, CURRENCY);

            assertThat(order.lines()).hasSize(2);
            assertThat(order.lines().get(0).productId()).isEqualTo("prod-A");
            assertThat(order.lines().get(1).productId()).isEqualTo("prod-B");
        }

        @Test
        @DisplayName("assigns the customer and seller identifiers")
        void place_assignsParties() {
            List<OrderLine> lines = List.of(line("prod-1", 1, "10.00"));

            Order order = Order.place(CUSTOMER_ID, SELLER_ID, lines, CURRENCY);

            assertThat(order.customerId()).isEqualTo(CUSTOMER_ID);
            assertThat(order.sellerId()).isEqualTo(SELLER_ID);
        }
    }

    @Nested
    @DisplayName("place() — invariant violations")
    class PlaceInvariants {

        @Test
        @DisplayName("rejects a null customerId")
        void place_throwsWhenCustomerIdNull() {
            List<OrderLine> lines = List.of(line("prod-1", 1, "10.00"));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Order.place(null, SELLER_ID, lines, CURRENCY))
                    .withMessageContaining("customerId");
        }

        @Test
        @DisplayName("rejects a blank customerId")
        void place_throwsWhenCustomerIdBlank() {
            List<OrderLine> lines = List.of(line("prod-1", 1, "10.00"));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Order.place("  ", SELLER_ID, lines, CURRENCY))
                    .withMessageContaining("customerId");
        }

        @Test
        @DisplayName("rejects a null sellerId")
        void place_throwsWhenSellerIdNull() {
            List<OrderLine> lines = List.of(line("prod-1", 1, "10.00"));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Order.place(CUSTOMER_ID, null, lines, CURRENCY));
        }

        @Test
        @DisplayName("rejects self-purchase (customerId == sellerId)")
        void place_throwsWhenCustomerEqualsSeller() {
            List<OrderLine> lines = List.of(line("prod-1", 1, "10.00"));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Order.place(CUSTOMER_ID, CUSTOMER_ID, lines, CURRENCY))
                    .withMessageContaining("cannot order their own products");
        }

        @Test
        @DisplayName("rejects a null line list")
        void place_throwsWhenLinesNull() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Order.place(CUSTOMER_ID, SELLER_ID, null, CURRENCY))
                    .withMessageContaining("at least one line");
        }

        @Test
        @DisplayName("rejects an empty line list")
        void place_throwsWhenLinesEmpty() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Order.place(CUSTOMER_ID, SELLER_ID, List.of(), CURRENCY))
                    .withMessageContaining("at least one line");
        }
    }

    @Nested
    @DisplayName("clearDomainEvents()")
    class ClearDomainEvents {

        @Test
        @DisplayName("returns a copy with no events")
        void clear_returnsOrderWithoutEvents() {
            Order order = Order.place(CUSTOMER_ID, SELLER_ID,
                    List.of(line("prod-1", 1, "10.00")), CURRENCY);
            assertThat(order.domainEvents()).hasSize(1);

            Order cleared = order.clearDomainEvents();

            assertThat(cleared.domainEvents()).isEmpty();
        }

        @Test
        @DisplayName("preserves every other field")
        void clear_preservesOtherData() {
            Order order = Order.place(CUSTOMER_ID, SELLER_ID,
                    List.of(line("prod-1", 2, "10.00")), CURRENCY);

            Order cleared = order.clearDomainEvents();

            assertThat(cleared.id()).isEqualTo(order.id());
            assertThat(cleared.customerId()).isEqualTo(order.customerId());
            assertThat(cleared.sellerId()).isEqualTo(order.sellerId());
            assertThat(cleared.total()).isEqualTo(order.total());
            assertThat(cleared.status()).isEqualTo(order.status());
            assertThat(cleared.lines()).isEqualTo(order.lines());
        }

        @Test
        @DisplayName("leaves the original untouched")
        void clear_doesNotMutateOriginal() {
            Order original = Order.place(CUSTOMER_ID, SELLER_ID,
                    List.of(line("prod-1", 1, "5.00")), CURRENCY);

            original.clearDomainEvents();

            assertThat(original.domainEvents()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("reconstitute()")
    class Reconstitute {

        @Test
        @DisplayName("rebuilds the order without emitting an event")
        void reconstitute_returnsOrderWithoutEvents() {
            OrderId id = OrderId.generate();
            List<OrderLine> lines = List.of(line("prod-1", 1, "15.00"));
            Money total = Money.of("15.00", CURRENCY);

            Order order = Order.reconstitute(id, CUSTOMER_ID, SELLER_ID,
                    lines, total, OrderStatus.PLACED, Instant.now());

            assertThat(order.id()).isEqualTo(id);
            assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
            assertThat(order.total()).isEqualTo(total);
            assertThat(order.domainEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("OrderLine")
    class OrderLineTests {

        @Test
        @DisplayName("computes lineTotal as quantity x unitPrice")
        void lineTotal_multipliesQuantityByUnitPrice() {
            OrderLine line = new OrderLine("prod-x", "Product X", 3, Money.of("12.50", "EUR"));

            assertThat(line.lineTotal()).isEqualTo(Money.of("37.50", "EUR"));
        }

        @Test
        @DisplayName("rejects a quantity below 1")
        void constructor_throwsWhenQuantityZero() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new OrderLine("prod-x", "Product X", 0, Money.of("10.00", "EUR")))
                    .withMessageContaining("Quantity");
        }

        @Test
        @DisplayName("rejects a zero unit price")
        void constructor_throwsWhenUnitPriceZero() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new OrderLine("prod-x", "Product X", 1, Money.zero("EUR")))
                    .withMessageContaining("Unit price");
        }

        @Test
        @DisplayName("rejects a blank productId")
        void constructor_throwsWhenProductIdBlank() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new OrderLine("", "Product X", 1, Money.of("5.00", "EUR")))
                    .withMessageContaining("productId");
        }
    }

    @Nested
    @DisplayName("OrderStatus transitions")
    class OrderStatusTransitions {

        @Test
        @DisplayName("PLACED may move to CONFIRMED or CANCELLED")
        void placed_allowsConfirmedAndCancelled() {
            assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
            assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("PLACED may not skip straight to SHIPPED")
        void placed_rejectsShipped() {
            assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        }

        @Test
        @DisplayName("CANCELLED is terminal — no transition is allowed")
        void cancelled_isTerminal() {
            assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
            for (OrderStatus next : OrderStatus.values()) {
                assertThat(OrderStatus.CANCELLED.canTransitionTo(next)).isFalse();
            }
        }

        @Test
        @DisplayName("REFUNDED is terminal")
        void refunded_isTerminal() {
            assertThat(OrderStatus.REFUNDED.isTerminal()).isTrue();
        }
    }
}
