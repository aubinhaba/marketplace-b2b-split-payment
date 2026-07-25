package com.aubin.order.infrastructure.adapter.out.messaging;

import com.aubin.commons.domain.Money;
import com.aubin.order.domain.model.OrderId;
import com.aubin.order.domain.model.OrderPlacedEvent;
import com.aubin.order.infrastructure.adapter.out.persistence.OutboxJpaEntity;
import com.aubin.order.infrastructure.adapter.out.persistence.OutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// A real ObjectMapper, not a mock: mocking it would hide genuine serialization failures
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventPublisher — writing to the outbox table")
class OutboxEventPublisherTest {

    @Mock
    private OutboxJpaRepository outboxRepository;

    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        publisher = new OutboxEventPublisher(outboxRepository, objectMapper);
    }

    private static OrderPlacedEvent orderPlacedEvent() {
        return new OrderPlacedEvent(
                OrderId.generate(),
                "11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222",
                Money.of("89.99", "EUR")
        );
    }

    @Nested
    @DisplayName("publish() — nominal path")
    class HappyPath {

        @Test
        @DisplayName("saves once per event")
        void publish_savesOncePerEvent() {
            publisher.publish(List.of(orderPlacedEvent()));

            verify(outboxRepository, times(1)).save(any(OutboxJpaEntity.class));
        }

        @Test
        @DisplayName("maps the event onto the outbox row")
        void publish_mapsEventFields() {
            OrderPlacedEvent event = orderPlacedEvent();
            ArgumentCaptor<OutboxJpaEntity> captor = ArgumentCaptor.forClass(OutboxJpaEntity.class);
            when(outboxRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            publisher.publish(List.of(event));

            OutboxJpaEntity saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(event.eventId());
            assertThat(saved.getEventType()).isEqualTo("order.placed");
            assertThat(saved.getAggregateId()).isEqualTo(event.orderId().value());
            assertThat(saved.isProcessed()).isFalse();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getProcessedAt()).isNull();
        }

        @Test
        @DisplayName("serializes the payload with ISO-8601 timestamps")
        void publish_writesJsonPayload() {
            ArgumentCaptor<OutboxJpaEntity> captor = ArgumentCaptor.forClass(OutboxJpaEntity.class);
            when(outboxRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            publisher.publish(List.of(orderPlacedEvent()));

            String payload = captor.getValue().getPayload();
            assertThat(payload).contains("\"eventId\"");
            assertThat(payload).contains("\"occurredOn\"");
            assertThat(payload).contains("\"customerId\"");
            assertThat(payload).contains("\"sellerId\"");
            assertThat(payload).contains("\"total\"");
            assertThat(payload).doesNotContain("1705328");
        }

        @Test
        @DisplayName("saves one row per event in the list")
        void publish_savesEveryEvent() {
            publisher.publish(List.of(orderPlacedEvent(), orderPlacedEvent()));

            verify(outboxRepository, times(2)).save(any(OutboxJpaEntity.class));
        }

        @Test
        @DisplayName("does nothing for an empty list")
        void publish_noOpOnEmptyList() {
            publisher.publish(List.of());

            verify(outboxRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("publish() — serialization failure")
    class SerializationFailure {

        @Test
        @DisplayName("fails the transaction instead of persisting a partial row")
        void publish_throwsWhenSerializationFails() throws Exception {
            ObjectMapper failingMapper = mock(ObjectMapper.class);
            when(failingMapper.writeValueAsString(any()))
                    .thenThrow(new JsonProcessingException("boom") {});

            OutboxEventPublisher publisherWithFailingMapper =
                    new OutboxEventPublisher(outboxRepository, failingMapper);

            assertThatThrownBy(() -> publisherWithFailingMapper.publish(List.of(orderPlacedEvent())))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("order.placed");

            verify(outboxRepository, never()).save(any());
        }
    }
}
