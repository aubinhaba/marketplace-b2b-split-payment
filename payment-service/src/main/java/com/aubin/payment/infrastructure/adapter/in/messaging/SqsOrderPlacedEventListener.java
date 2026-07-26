package com.aubin.payment.infrastructure.adapter.in.messaging;

import com.aubin.payment.application.port.in.ProcessPaymentUseCase;
import com.aubin.payment.application.port.in.ProcessPaymentUseCase.ProcessPaymentCommand;
import com.aubin.payment.application.port.out.IdempotencyStore;
import com.aubin.payment.infrastructure.adapter.in.messaging.dto.OrderPlacedEventDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SqsOrderPlacedEventListener {

    private static final Logger log = LoggerFactory.getLogger(SqsOrderPlacedEventListener.class);

    private final IdempotencyStore idempotencyStore;
    private final ProcessPaymentUseCase processPaymentUseCase;
    private final ObjectMapper objectMapper;

    public SqsOrderPlacedEventListener(
            IdempotencyStore idempotencyStore,
            ProcessPaymentUseCase processPaymentUseCase,
            ObjectMapper objectMapper) {
        this.idempotencyStore = idempotencyStore;
        this.processPaymentUseCase = processPaymentUseCase;
        this.objectMapper = objectMapper;
    }

    @SqsListener("${aws.sqs.order-placed-queue}")
    public void onOrderPlaced(String rawMessage) {
        OrderPlacedEventDto event;
        try {
            event = objectMapper.readValue(rawMessage, OrderPlacedEventDto.class);
        } catch (JsonProcessingException e) {
            // Rethrown so the message is redelivered and eventually lands in the DLQ instead of vanishing
            throw new RuntimeException(
                    "Failed to deserialize OrderPlacedEvent — malformed JSON, sending to DLQ: " + e.getMessage(), e);
        }

        if (!idempotencyStore.tryMark(event.eventId())) {
            // Returning acknowledges the message. Throwing here would redeliver a duplicate forever
            log.info("OrderPlacedEvent {} already processed (SQS duplicate) — skipping", event.eventId());
            return;
        }

        var command = new ProcessPaymentCommand(
                event.orderId().value(),
                event.sellerId(),
                event.customerId(),
                event.total().amount(),
                event.total().currency()
        );

        processPaymentUseCase.process(command);

        log.info("OrderPlacedEvent {} processed successfully — order={}, seller={}",
                event.eventId(), event.orderId().value(), event.sellerId());
    }
}
