# ADR-003 — Outbox Pattern for Event Publication (vs CDC with Debezium)

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-05-10 |
| **Deciders** | Aubin |
| **Tags** | `outbox`, `event-driven`, `messaging`, `sqs`, `reliability`, `at-least-once` |

## Context

Every service must publish an SQS event once it has committed a database transaction. There is no
distributed transaction spanning Aurora PostgreSQL and SQS, so both naive strategies fail:

```java
// Publish before the commit
sqsTemplate.send("order-events", event);   // succeeds
orderRepository.save(order);               // crashes -> event published, nothing stored

// Publish after the commit
orderRepository.save(order);               // commits
sqsTemplate.send("order-events", event);   // crashes -> nothing published, consumers never told
```

Either way the database and the queue disagree. In a payment system that is not acceptable.

## Decision

Adopt the **Outbox pattern** for every event publication in the platform.

The event is inserted into an `outbox` table inside the same transaction as the business write, so the
two commit together or not at all. A scheduled poller then publishes pending rows to SQS and marks them
processed.

```
Transaction (atomic)                        Poller (@Scheduled, fixedDelay)
  INSERT INTO orders ...                      SELECT ... WHERE processed = false
  INSERT INTO outbox (...)                      ORDER BY created_at LIMIT 50
  COMMIT                                        FOR UPDATE SKIP LOCKED
                                              send to SQS
                                              UPDATE outbox SET processed = true
```

Two details carry most of the weight:

**`FOR UPDATE SKIP LOCKED`.** Several service instances poll concurrently. `SKIP LOCKED` makes each one
claim a disjoint subset instead of blocking on rows another instance already holds.

**`fixedDelay`, not `fixedRate`.** A fixed rate starts the next cycle on schedule even if the current one
is still running. With a slow SQS, two threads would then read the outbox at once — and because their
locks are held by different transactions, `SKIP LOCKED` would not prevent a duplicate send.

The publisher and the poller are deliberately separate classes: the first only writes rows (inside the
caller's transaction), the second only publishes them.

## Alternatives considered

**CDC with Debezium** — rejected. Technically stronger on latency (~100 ms of WAL streaming against a
polling interval) and needs no outbox table, but it requires Debezium, Kafka Connect and Kafka in the
runtime, plus logical WAL decoding on Aurora Serverless. For a B2B payment flow where a couple of
seconds is fine, that operational surface is not justified.

**`@TransactionalEventListener(phase = AFTER_COMMIT)`** — rejected. Publication happens after the commit
but still on the request thread, so a slow SQS delays the HTTP response, and a crash between commit and
publication loses the event outright — exactly the failure mode this ADR exists to remove.

**Manual `TransactionSynchronizationManager`** — rejected. Same flaw as above, with fragile
Spring-internal code on top.

## Consequences

**Benefits**
- At-least-once delivery: if the order is committed, the event will be published.
- The business transaction knows only about the database, never about SQS.
- The table doubles as an audit trail of everything produced, and replaying an event is a one-row update.
- The poller is an ordinary Spring bean, testable against a real PostgreSQL without mocking SQS.

**Costs**
- Publication latency equal to the poll interval (5 s by default) rather than milliseconds.
- The table grows without bound; processed rows need a purge job.
- Consumers **must** be idempotent. A crash between the SQS send and the `processed = true` update
  republishes the event, so every listener has to dedupe on the event id.
- `payload` is stored as `TEXT` rather than `JSONB` so the same DDL runs on H2 in tests. Moving to
  `JSONB` only pays off once we need to index inside the payload.

## Enforcement

- `PlaceOrderIT.placeOrder_writesOutboxEventAtomically` asserts, over direct JDBC on a real PostgreSQL,
  that the business row and the outbox row are committed together.
- `OutboxPollerTest.processOutbox_propagatesSqsFailure` asserts that a failed send leaves the row
  unprocessed instead of silently dropping the event.

## References

- Richardson, C. — *Pattern: Transactional outbox* — https://microservices.io/patterns/data/transactional-outbox.html
- Kleppmann, M. — *Designing Data-Intensive Applications* (2017), ch. 11
