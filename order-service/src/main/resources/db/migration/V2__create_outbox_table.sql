-- Outbox table (ADR-003). Delivery is at-least-once: consumers must dedupe on the event id.
-- payload is TEXT rather than JSONB: JSONB is PostgreSQL-only and the H2-backed tests need the same DDL.

CREATE TABLE outbox
(
    -- DomainEvent.eventId(), the consumer's idempotency key
    id           UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    -- Producing aggregate: also the MessageGroupId for FIFO queues
    aggregate_id VARCHAR(36)  NOT NULL,
    payload      TEXT         NOT NULL,
    processed    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT pk_outbox PRIMARY KEY (id)
);

-- Partial index: the poller only ever scans unprocessed rows, and those are the small minority.
CREATE INDEX idx_outbox_pending ON outbox (created_at) WHERE processed = false;
