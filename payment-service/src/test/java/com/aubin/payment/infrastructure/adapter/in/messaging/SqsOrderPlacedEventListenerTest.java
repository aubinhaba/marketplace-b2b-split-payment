package com.aubin.payment.infrastructure.adapter.in.messaging;

import com.aubin.payment.application.port.in.ProcessPaymentUseCase;
import com.aubin.payment.application.port.in.ProcessPaymentUseCase.ProcessPaymentCommand;
import com.aubin.payment.application.port.out.IdempotencyStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SqsOrderPlacedEventListener — idempotency and deserialization")
class SqsOrderPlacedEventListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ORDER_ID_VALUE = "00000000-0000-0000-0000-000000000002";

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private ProcessPaymentUseCase processPaymentUseCase;

    private SqsOrderPlacedEventListener listener;

    @BeforeEach
    void setUp() {
        var objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        listener = new SqsOrderPlacedEventListener(idempotencyStore, processPaymentUseCase, objectMapper);
    }

    private static String buildValidJson(UUID eventId) {
        return """
                {
                  "eventId":    "%s",
                  "occurredOn": "2024-01-15T10:30:00Z",
                  "orderId":    {"value": "%s"},
                  "customerId": "customer-1",
                  "sellerId":   "seller-abc",
                  "total":      {"amount": 150.00, "currency": "EUR"},
                  "eventType":  "order.placed",
                  "aggregateId": "%s"
                }
                """.formatted(eventId, ORDER_ID_VALUE, ORDER_ID_VALUE);
    }

    @Test
    @DisplayName("maps a new event onto the use case command")
    void onOrderPlaced_new_event_calls_use_case_with_correct_command() {
        when(idempotencyStore.tryMark(EVENT_ID)).thenReturn(true);

        listener.onOrderPlaced(buildValidJson(EVENT_ID));

        var commandCaptor = ArgumentCaptor.forClass(ProcessPaymentCommand.class);
        verify(processPaymentUseCase).process(commandCaptor.capture());

        var command = commandCaptor.getValue();
        assertThat(command.orderId()).isEqualTo(ORDER_ID_VALUE);
        assertThat(command.sellerId()).isEqualTo("seller-abc");
        assertThat(command.customerId()).isEqualTo("customer-1");
        assertThat(command.amount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(command.currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("skips a duplicate without throwing, so SQS still acknowledges it")
    void onOrderPlaced_duplicate_event_does_not_call_use_case() {
        when(idempotencyStore.tryMark(EVENT_ID)).thenReturn(false);

        assertThatNoException().isThrownBy(() -> listener.onOrderPlaced(buildValidJson(EVENT_ID)));

        verifyNoInteractions(processPaymentUseCase);
    }

    @Test
    @DisplayName("guards on the event id carried by the message")
    void onOrderPlaced_checks_idempotency_with_correct_event_id() {
        UUID specificEventId = UUID.randomUUID();
        when(idempotencyStore.tryMark(specificEventId)).thenReturn(true);

        listener.onOrderPlaced(buildValidJson(specificEventId));

        verify(idempotencyStore).tryMark(specificEventId);
    }

    @Test
    @DisplayName("throws on malformed JSON so the message is redelivered instead of dropped")
    void onOrderPlaced_malformed_json_throws_runtime_exception() {
        assertThatThrownBy(() -> listener.onOrderPlaced("{ this is not valid json }"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to deserialize OrderPlacedEvent");

        verifyNoInteractions(idempotencyStore);
        verifyNoInteractions(processPaymentUseCase);
    }

    @Test
    @DisplayName("ignores unknown fields, so a producer can add them without breaking this consumer")
    void onOrderPlaced_unknown_json_fields_are_ignored() {
        String jsonWithExtraField = """
                {
                  "eventId":    "%s",
                  "occurredOn": "2024-01-15T10:30:00Z",
                  "orderId":    {"value": "%s"},
                  "customerId": "customer-1",
                  "sellerId":   "seller-1",
                  "total":      {"amount": 50.00, "currency": "EUR"},
                  "eventType":  "order.placed",
                  "priority":   "HIGH"
                }
                """.formatted(EVENT_ID, ORDER_ID_VALUE);

        when(idempotencyStore.tryMark(EVENT_ID)).thenReturn(true);

        assertThatNoException().isThrownBy(() -> listener.onOrderPlaced(jsonWithExtraField));
        verify(processPaymentUseCase).process(any());
    }
}
