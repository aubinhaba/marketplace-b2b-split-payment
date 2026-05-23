# Marketplace B2B Split Payment

A production-grade B2B marketplace platform built around the Stripe Connect split-payment model. The platform collects payments on behalf of sellers, deducts a commission, and transfers funds to connected accounts — implemented as a Java 21 / Spring Boot microservices monorepo with strict hexagonal architecture.

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

**ArchUnit enforces this boundary at every build.** `domain/` and `application/` have zero Spring, JPA, or AWS imports — any violation fails CI immediately.

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

**Outbox pattern** — every domain event is written to an `outbox` table in the same transaction as the business INSERT. A `@Scheduled` poller reads pending rows and publishes to SQS. This guarantees exactly-once delivery from the database perspective and decouples the SQS call from the business transaction.

**SQS idempotency** — every `@SqsListener` guards with Redis `SETNX` on `eventId` before processing. Duplicate messages (SQS at-least-once delivery) are silently dropped after the key is set.

**Stripe Connect** — payments use `application_fee_amount` + `transfer_data.destination` on a single PaymentIntent. The seller's Connected Account ID (`sellerId`) is carried through the domain model from order creation to payout — never inferred at the infrastructure boundary.

**W3C TraceContext over SQS** — Spring does not propagate trace context through SQS automatically. Each outbox poller injects `traceparent`/`tracestate` as SQS `MessageAttributes`; each listener extracts and restores the span before processing. This provides end-to-end trace continuity across service boundaries in Grafana Tempo.

**Ownership via `@PostAuthorize`** — `GET /payments/{id}` and `DELETE /payments/{id}/cancel` enforce that the authenticated seller can only access their own resources. A custom `PaymentAccessGuard` implements `PermissionEvaluator` and is invoked by Spring Security before the response is returned.

**`NUMERIC(19,4)` for money** — all monetary amounts are stored as `NUMERIC(19,4)` in PostgreSQL and handled as `BigDecimal` in Java. The `Money` value object normalizes scale on construction per ISO 4217 (EUR=2, XOF=0, BHD=3).

**SQS FIFO `MessageGroupId` per aggregate** — set to `orderId` or `sellerId`, not a global group. A global group caps throughput at 300 msg/s across the entire queue. Per-aggregate grouping preserves ordering per business entity and scales horizontally.

## Shared modules

**`marketplace-commons`** — Value Objects used across all services: `Money`, `AggregateId`, `DomainEvent`, `BusinessException`, `@CurrencyCode` validation annotation.

**`marketplace-commons-security`** — Spring Security auto-configuration: JWT converter, `MarketplacePrincipal` (wraps `sellerId`, scopes, roles), pluggable for both Keycloak and Cognito via a strategy interface.

## Local setup

### Prerequisites

- Java 21 (Temurin recommended)
- Maven 3.9+ (no Maven Wrapper — use `mvn` from PATH)
- Docker Desktop running

### Start infrastructure

```powershell
docker compose up -d
```

Starts Redis (idempotency), Grafana Tempo (traces), and Grafana (`:3000`, admin/admin).

### Start Keycloak (optional — only needed for JWT auth testing)

```powershell
docker compose -f docker-compose.keycloak.yml up -d
```

Keycloak runs on `:8180` with a pre-configured `marketplace` realm.

### Start a service

```powershell
mvn spring-boot:run -pl payment-service -s settings-local.xml `
    "-Dspring-boot.run.useTestClasspath=true" `
    "-Dspring-boot.run.profiles=local"
```

- `-s settings-local.xml` — redirects to Maven Central (no internal Nexus required)
- `useTestClasspath=true` — adds H2 (declared `scope=test`) to the runtime classpath
- `profiles=local` — activates `application-local.yml` (H2 in-memory, SQS disabled, Keycloak on `:8180`)

Health check: `GET http://localhost:8081/actuator/health`

### Ports

| Service | Port |
|---|---|
| order-service | 8080 |
| payment-service | 8081 |
| ledger-service | 8082 |
| payout-service | 8083 |
| Redis | 6379 |
| Keycloak | 8180 |
| Grafana | 3000 |
| Tempo (OTLP) | 4318 |

## Commands

```powershell
# Full build, skip integration tests
mvn clean install -s settings-local.xml -DskipITs

# ArchUnit only (< 5 s, run often)
mvn test -pl payment-service -s settings-local.xml "-Dtest=HexagonalArchitectureTest"

# Coverage report — open target/site/jacoco/index.html
mvn verify -pl payment-service -s settings-local.xml -DskipITs

# Integration tests (requires Docker)
mvn verify -pl payment-service -s settings-local.xml
```

JaCoCo gate: **90% minimum on `domain/` and `application/`**. Infrastructure adapters are excluded.
