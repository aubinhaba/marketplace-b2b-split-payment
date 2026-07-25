package com.aubin.order.infrastructure.adapter.out.messaging;

import com.aubin.commons.domain.DomainEvent;
import com.aubin.order.application.port.out.OrderEventPublisher;
import com.aubin.order.infrastructure.adapter.out.persistence.OutboxJpaEntity;
import com.aubin.order.infrastructure.adapter.out.persistence.OutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

// Insert only, the SQS send is OutboxPoller's job: a direct send could succeed while the transaction rolls back
@Component
public class OutboxEventPublisher implements OrderEventPublisher {

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            OutboxJpaEntity entity = new OutboxJpaEntity();
            entity.setId(event.eventId());
            entity.setEventType(event.eventType());
            entity.setAggregateId(event.aggregateId());
            entity.setPayload(serialize(event));
            entity.setProcessed(false);
            entity.setCreatedAt(Instant.now());

            outboxRepository.save(entity);
        }
    }

    // A serialization failure is a programming error: fail the transaction rather than swallow it
    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Cannot serialize event " + event.eventType() + " id=" + event.eventId(), e);
        }
    }
}
