# Marketplace B2B Split Payment

A B2B marketplace platform implementing the Stripe Connect split-payment model. Built as a Java 21 / Spring Boot microservices monorepo following a strict Hexagonal Architecture, it orchestrates payment collection, automated commission deduction, and payouts to connected seller accounts.

## Status

Built one bounded context at a time. What is in the repository today:

- **`marketplace-commons`** — shared value objects: `Money` (ISO 4217 scale normalization), `AggregateId`,
  `DomainEvent`, `BusinessException`, and the `@CurrencyCode` constraint.
- **`order-service`** — complete: hexagonal domain with the `Order` aggregate and its invariants, the
  Outbox pattern end to end (transactional publisher plus a `FOR UPDATE SKIP LOCKED` poller), a REST
  adapter returning RFC 7807 errors, Flyway migrations, four ArchUnit boundary rules, and a Testcontainers
  integration test that proves the business row and the outbox row commit together.
- **`payment-service`** — partial skeleton. Its package layout does not yet follow the structure below,
  and the Stripe, resilience and idempotency work described further down is not implemented here yet.

`ledger-service`, `payout-service` and the shared security module are part of the design described below;
they are not in the repository yet. Sections marked as design state the target, not the current state.

## Architecture overview

```
┌─────────────────────────────────────────────────────────────────┐
│                          API Gateway                            │
└────────┬────────────┬────────────┬──────────────────────────────┘
         │            │            │
    order-service  payment-service  payout-service
      :8080           :8081           :8083
         │            │            │
         └────────────┼────────────┘
              SQS FIFO / Standard (Outbox pattern)
                       │
                 ledger-service
                   :8082
```

Four bounded contexts, each a self-contained Spring Boot service with its own PostgreSQL schema, Flyway migrations, and hexagonal package structure. Inter-service communication is exclusively event-driven through AWS SQS. No synchronous service-to-service calls.

### Bounded contexts

| Service | Responsibility |
|---|---|
| **order-service** | Order lifecycle, Outbox event publishing |
| **payment-service** | Stripe Connect authorization, cancellation, ownership enforcement |
| **ledger-service** | Double-entry accounting, append-only audit trail (Envers) |
| **payout-service** | Scheduled Stripe Connect transfers to seller connected accounts |

### Hexagonal structure (per service)

```
com.aubin.<service>/
├── domain/
│   ├── model/       # Aggregates and Value Objects (Java 21 records)
│   ├── service/     # Domain services
│   └── exception/   # Business exceptions — zero Spring/JPA imports
├── application/
│   ├── port/in/     # Use case interfaces (inbound)
│   ├── port/out/    # Repository, Gateway, Publisher ports (outbound)
│   └── service/     # Use case implementations
└── infrastructure/
    ├── adapter/in/rest/          # Controllers, DTOs, MapStruct mappers
    ├── adapter/in/messaging/     # @SqsListener
    ├── adapter/out/persistence/  # JPA entities, repositories, MapStruct mappers
    ├── adapter/out/psp/          # Stripe adapter
    ├── adapter/out/messaging/    # Outbox publisher + scheduler
    └── config/
```

**ArchUnit enforces this boundary at every build.** `domain/` and `application/` have zero Spring, JPA, or AWS imports — any violation fails CI immediately. See [ADR-001](adr/ADR-001-hexagonal-architecture-ddd.md) and [ADR-005](adr/ADR-005-archunit-architecture-guardrail.md).

Architecture decisions are recorded under [`adr/`](adr).

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 — records, sealed classes, pattern matching in switch |
| Framework | Spring Boot 3.4.5, Spring Cloud 2023.0.3 |
| Messaging | AWS SQS FIFO (per-aggregate `MessageGroupId`) + Standard, Spring Cloud AWS 3.2.0 |
| Persistence | Aurora PostgreSQL Serverless v2, Spring Data JPA, Flyway |
| In-memory | Redis — SQS idempotency (SETNX + TTL), distributed scheduler lock |
| Resilience | Resilience4j 2.2.0 — CircuitBreaker + Retry + Bulkhead + TimeLimiter on every PSP call |
| Security | Spring Security OAuth2 Resource Server, Keycloak, custom `PermissionEvaluator` |
| Mapping | MapStruct 1.6.3 (compile-time), Lombok |
| Observability | OpenTelemetry 1.42.1, W3C TraceContext propagation over SQS, Grafana Tempo |
| Architecture tests | ArchUnit 1.3.0 — hexagonal boundaries + package conventions |
| Integration tests | Testcontainers 1.20.3, LocalStack, WireMock |
| Build | Maven 3.9 monorepo — single parent POM, version lockstep |

## Key design decisions

**Outbox pattern** — every domain event is written to an `outbox` table in the same transaction as the business INSERT, so the two commit together or not at all. A `@Scheduled` poller claims pending rows with `FOR UPDATE SKIP LOCKED` — concurrent instances get disjoint subsets — publishes them to SQS, and marks them processed in the same transaction, so a failed send is simply retried. Delivery is at-least-once: consumers dedupe on the event id. `fixedDelay` rather than `fixedRate`, otherwise a slow SQS would overlap two cycles whose locks sit in different transactions. See [ADR-003](adr/ADR-003-outbox-pattern-vs-cdc.md).

**SQS idempotency** — every `@SqsListener` guards with Redis `SETNX` on `eventId` before processing. Duplicate messages (SQS at-least-once delivery) are silently dropped after the key is set.

**Stripe Connect** — payments use `application_fee_amount` + `transfer_data.destination` on a single PaymentIntent. The seller's Connected Account ID (`sellerId`) is carried through the domain model from order creation to payout — never inferred at the infrastructure boundary.

**W3C TraceContext over SQS** — Spring does not propagate trace context through SQS automatically. Each outbox poller injects `traceparent`/`tracestate` as SQS `MessageAttributes`; each listener extracts and restores the span before processing. This provides end-to-end trace continuity across service boundaries in Grafana Tempo.

**Ownership via `@PostAuthorize`** — `GET /payments/{id}` and `DELETE /payments/{id}/cancel` enforce that the authenticated seller can only access their own resources. A custom `PaymentAccessGuard` implements `PermissionEvaluator` and is invoked by Spring Security before the response is returned.

**`NUMERIC(19,4)` for money** — all monetary amounts are stored as `NUMERIC(19,4)` in PostgreSQL and handled as `BigDecimal` in Java. The `Money` value object normalizes scale on construction per ISO 4217 (EUR=2, XOF=0, BHD=3).

**SQS FIFO `MessageGroupId` per aggregate** — set to `orderId` or `sellerId`, not a global group. A global group caps throughput at 300 msg/s across the entire queue. Per-aggregate grouping preserves ordering per business entity and scales horizontally.

## Shared modules

**`marketplace-commons`** — Value Objects used across all services: `Money`, `AggregateId`, `DomainEvent`, `BusinessException`, `@CurrencyCode` validation annotation.

A `marketplace-commons-security` module carrying the JWT converter and the authenticated principal is part
of the design, pluggable across identity providers through a strategy interface. It is not in the
repository yet.

## Build and test

### Prerequisites

- Java 21 (Temurin recommended)
- Maven 3.9+ (no Maven Wrapper — use `mvn` from PATH)
- Docker, for the integration tests only

### Commands

```bash
# Compile and run the unit, architecture and web-layer tests
mvn clean install -DskipITs

# Everything, including the Testcontainers integration tests (needs Docker)
mvn verify

# The architecture rules alone — a couple of seconds, worth running often
mvn test -pl order-service -Dtest=HexagonalArchitectureTest
```

`mvn verify` starts a real PostgreSQL 16 container: `PlaceOrderIT` runs the full HTTP → controller → use
case → JPA → PostgreSQL path and asserts the outbox guarantee over direct JDBC.

On Docker Engine 29 or newer, Testcontainers fails with *"Could not find a valid Docker environment"*: the
bundled docker-java negotiates API version 1.32, below the 1.40 minimum those daemons accept. Pin a
supported version for the run:

```bash
mvn verify -Dapi.version=1.44
```

JaCoCo reports are generated under each module's `target/site/jacoco/`. Coverage is tracked on `domain/`
and `application/`; infrastructure adapters are excluded.

### Running a service

`order-service` expects PostgreSQL on `localhost:5432` (database `order_db`, user `order_user`), overridable
through `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` and `DB_PASSWORD`. Flyway applies the schema at
startup; Hibernate only validates it.

```bash
mvn spring-boot:run -pl order-service
```

Health check: `GET http://localhost:8080/actuator/health`

### Ports

| Service | Port |
|---|---|
| order-service | 8080 |
| payment-service | 8081 |
| ledger-service | 8082 |
| payout-service | 8083 |
