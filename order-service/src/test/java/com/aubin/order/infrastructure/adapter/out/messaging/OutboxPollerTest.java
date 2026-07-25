package com.aubin.order.infrastructure.adapter.out.messaging;

import com.aubin.order.infrastructure.adapter.out.persistence.OutboxJpaEntity;
import com.aubin.order.infrastructure.adapter.out.persistence.OutboxJpaRepository;
import io.awspring.cloud.sqs.operations.SqsOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// queueName is an @Value field, so it is set reflectively rather than standing up a Spring context
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPoller — publishing pending events")
class OutboxPollerTest {

    @Mock
    private OutboxJpaRepository outboxRepository;

    @Mock
    private SqsOperations sqsOperations;

    private OutboxPoller poller;

    @BeforeEach
    void setUp() {
        poller = new OutboxPoller(outboxRepository, sqsOperations);
        ReflectionTestUtils.setField(poller, "queueName", "order-events-test");
    }

    private static OutboxJpaEntity pendingEvent(String payload) {
        OutboxJpaEntity entity = new OutboxJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventType("order.placed");
        entity.setAggregateId(UUID.randomUUID().toString());
        entity.setPayload(payload);
        entity.setProcessed(false);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    @Nested
    @DisplayName("processOutbox() — nominal path")
    class HappyPath {

        @Test
        @DisplayName("sends one message per pending event")
        void processOutbox_sendsOneMessagePerEvent() {
            when(outboxRepository.findPendingEventsForUpdate())
                    .thenReturn(List.of(pendingEvent("{\"type\":\"order.placed\"}")));

            poller.processOutbox();

            verify(sqsOperations, times(1)).send(any());
        }

        @Test
        @DisplayName("marks the event processed once it has been sent")
        void processOutbox_marksEventProcessed() {
            when(outboxRepository.findPendingEventsForUpdate())
                    .thenReturn(List.of(pendingEvent("{\"test\":true}")));
            ArgumentCaptor<OutboxJpaEntity> captor = ArgumentCaptor.forClass(OutboxJpaEntity.class);
            when(outboxRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            poller.processOutbox();

            OutboxJpaEntity saved = captor.getValue();
            assertThat(saved.isProcessed()).isTrue();
            assertThat(saved.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("drains the whole batch in one cycle")
        void processOutbox_handlesWholeBatch() {
            when(outboxRepository.findPendingEventsForUpdate()).thenReturn(List.of(
                    pendingEvent("{\"n\":1}"),
                    pendingEvent("{\"n\":2}"),
                    pendingEvent("{\"n\":3}")
            ));

            poller.processOutbox();

            verify(sqsOperations, times(3)).send(any());
            verify(outboxRepository, times(3)).save(any(OutboxJpaEntity.class));
        }

        @Test
        @DisplayName("does nothing when the outbox is empty")
        void processOutbox_noOpWhenEmpty() {
            when(outboxRepository.findPendingEventsForUpdate()).thenReturn(List.of());

            poller.processOutbox();

            verify(sqsOperations, never()).send(any());
            verify(outboxRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("processOutbox() — SQS failure")
    class SqsFailure {

        @Test
        @DisplayName("propagates the failure and leaves the event unprocessed")
        void processOutbox_propagatesSqsFailure() {
            when(outboxRepository.findPendingEventsForUpdate())
                    .thenReturn(List.of(pendingEvent("{\"test\":true}")));
            doThrow(new RuntimeException("SQS unavailable")).when(sqsOperations).send(any());

            assertThatThrownBy(() -> poller.processOutbox())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("SQS unavailable");

            verify(outboxRepository, never()).save(any());
        }
    }
}
