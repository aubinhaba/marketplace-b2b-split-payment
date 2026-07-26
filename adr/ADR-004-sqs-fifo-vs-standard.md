# ADR-004 — FIFO Queues Only Where Ordering Is an Accounting Requirement

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-05-10 |
| **Deciders** | Aubin |
| **Tags** | `sqs`, `fifo`, `messaging`, `ordering`, `throughput` |

## Context

SQS offers two queue types, and they trade ordering against throughput:

| | Standard | FIFO |
|---|---|---|
| Throughput | effectively unbounded | 300 msg/s, or 3 000/s in high-throughput mode |
| Ordering | best effort | guaranteed within a `MessageGroupId` |
| Deduplication | none | `MessageDeduplicationId`, 5-minute window |

The tempting move is FIFO everywhere "to be safe". That is a mistake twice over: it caps throughput on
queues that never needed ordering, and its deduplication window creates a false sense of safety that
hides missing application-level idempotency.

## Decision

Use FIFO **only where event order changes the resulting state or the books.** Everything else is Standard.

FIFO, keyed by the aggregate that owns the sequence:

| Queue | `MessageGroupId` | Why ordering matters |
|---|---|---|
| `order-events.fifo` | `orderId` | An order moves PENDING → PAID → SHIPPED; applying SHIPPED first corrupts the state |
| `payment-events.fifo` | `orderId` | `Refunded` before `Captured` puts the ledger out of balance |
| `payout-events.fifo` | `payoutId` | Scheduled → Processing → Completed/Failed is a strict cycle |

Standard, because consumers are idempotent and cross-aggregate order is meaningless:
`ledger-events` (high volume, fan-out to reporting and search), `psp-webhooks` (deduplicated at the
gateway), `notification-events` (delivery order has no bearing on consistency).

**`MessageGroupId` is always the aggregate id, never a constant.** A constant group id puts every message
in one sequence and silently caps the whole service at 300 msg/s. Distinct aggregates stay parallel.

## Alternatives considered

**FIFO everywhere** — rejected. It would throttle `ledger-events` and `psp-webhooks` for no benefit, and
its 5-minute deduplication window does not cover a redelivery that arrives six minutes later, so
consumers still need their own idempotency check.

**Standard everywhere plus an application-level sequence number** — rejected. Consumers would have to
buffer, reorder and requeue out-of-sequence events: reimplementing FIFO with more code and weaker
guarantees.

**Kafka** — out of scope. It orders per partition, which is the same idea, and adds native replay, but it
brings a cluster to operate. SQS is sufficient for B2B marketplace volume.

## Consequences

**Benefits**
- Ordering guaranteed where the books depend on it, with no application code.
- No artificial throughput ceiling on the high-volume paths.
- `MessageDeduplicationId` set to the event id absorbs fast redeliveries for free.

**Costs**
- Two queue types to operate, so the `.fifo` suffix convention has to be respected.
- Application idempotency is still mandatory. FIFO deduplication does not replace the Redis `SETNX`
  guard in `RedisIdempotencyAdapter` — see ADR-003.
- A constant `MessageGroupId` degrades throughput silently rather than failing, so it will not show up in
  tests. It has to be caught in review.

## Current state

`order-events` is the only queue wired today, and the local and test setups run it as a Standard queue —
`OutboxPoller` sends the payload without a group id. The FIFO topology above is the target for the
deployed environment; the per-aggregate group id is already available, since every `DomainEvent` exposes
`aggregateId()`.

## References

- AWS — *Amazon SQS FIFO queues* — https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues.html
- AWS — *High throughput for FIFO queues* — https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/high-throughput-fifo.html
