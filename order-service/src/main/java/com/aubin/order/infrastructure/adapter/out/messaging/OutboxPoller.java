package com.aubin.order.infrastructure.adapter.out.messaging;

import com.aubin.order.infrastructure.adapter.out.persistence.OutboxJpaEntity;
import com.aubin.order.infrastructure.adapter.out.persistence.OutboxJpaRepository;
import io.awspring.cloud.sqs.operations.SqsOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxJpaRepository outboxRepository;
    private final SqsOperations sqsOperations;

    @Value("${order.outbox.sqs.queue-name:order-events}")
    private String queueName;

    public OutboxPoller(OutboxJpaRepository outboxRepository, SqsOperations sqsOperations) {
        this.outboxRepository = outboxRepository;
        this.sqsOperations = sqsOperations;
    }

    // fixedDelay not fixedRate: a slow batch would put two threads on the outbox, and SKIP LOCKED does not protect across transactions
    @Scheduled(fixedDelayString = "${order.outbox.poll-delay-ms:5000}")
    @Transactional
    public void processOutbox() {
        List<OutboxJpaEntity> pendingEvents = outboxRepository.findPendingEventsForUpdate();

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxJpaEntity event : pendingEvents) {
            sqsOperations.send(to -> to.queue(queueName).payload(event.getPayload()));

            event.setProcessed(true);
            event.setProcessedAt(Instant.now());
            outboxRepository.save(event);

            log.debug("Published event {} ({}) for aggregate {}",
                    event.getId(), event.getEventType(), event.getAggregateId());
        }

        log.info("Published {} event(s) to SQS [queue={}]", pendingEvents.size(), queueName);
    }
}
