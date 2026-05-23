# ADR-001 — Hexagonal Architecture + DDD Tactical Design

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-05-10 |
| **Deciders** | Aubin |
| **Tags** | `architecture`, `hexagonal`, `ddd` |

## Context

The platform is a multi-tenant B2B payment system with four microservices, complex business rules (split payment, double-entry ledger, chargeback saga), and strict compliance requirements (PCI DSS SAQ-A, full audit trail).

Without explicit architectural boundaries, the following risks materialize at scale:

- **Framework-domain coupling** — migrating Spring Boot or swapping JPA for R2DBC requires rewriting business logic alongside technical adapters.
- **Slow, brittle tests** — a domain that depends on Spring cannot be tested without an `ApplicationContext`.
- **Anemic model** — business rules drift toward `@Service` transaction scripts; JPA entities become glorified DTOs.
- **Inconsistent structure** — without a shared convention, each service is organized differently, raising onboarding cost.

## Decision

Adopt **Hexagonal Architecture (Ports & Adapters)** combined with **DDD tactical design** across all microservices.

### Package structure (enforced in every service)

```
com.aubin.<service>/
├── domain/
│   ├── model/       # Aggregates, Value Objects (records), Domain Events
│   ├── service/     # Domain services spanning multiple aggregates
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

**Invariant:** `domain/` and `application/` have zero Spring, JPA, or AWS imports. Enforced mechanically by ArchUnit at every build (see ADR-005).

### DDD tactical patterns

| Pattern | Usage |
|---|---|
| **Aggregate Root** | `Order`, `Payment`, `LedgerEntry`, `PayoutBatch` |
| **Value Object** | `Money`, `OrderId`, `PaymentStatus`, `SellerId` — Java 21 records |
| **Domain Event** | `OrderPlaced`, `PaymentAuthorized`, `PaymentCaptured` |
| **Repository** | Interface in `application/port/out/`, implementation in `infrastructure/adapter/out/persistence/` |
| **Factory Method** | `Payment.create()`, `LedgerEntry.create()` — invariants validated at construction |

## Alternatives considered

**Layered architecture (Controller → Service → Repository)** — rejected. Business logic migrates naturally toward `@Service` scripts; entities become anemic. Testing requires a full Spring context.

**Full CQRS / Event Sourcing (Axon)** — rejected. Event sourcing eliminates normal persistence tables and introduces Axon Server as a required runtime dependency. The added complexity is disproportionate to the problem scope.

**Spring Modulith** — not applicable. The platform is multi-service by design (ECS Fargate, SQS for inter-service communication). Spring Modulith targets a modular monolith.

## Consequences

**Benefits**
- Domain and application layers are testable without Spring — fast, stable unit tests.
- Adapters are swappable without touching business logic (e.g., replacing Stripe with Adyen only changes `adapter/out/psp/`).
- Architectural conformance is machine-verified in CI, not relying on code review discipline.
- Consistent structure across all services reduces onboarding cost.

**Costs**
- More files per concept — domain model, inbound port, outbound port, adapter. Acceptable overhead on a long-lived system.
- Explicit mapping required at every boundary (handled by MapStruct — no JPA entities passed directly to controllers).

## Enforcement

- `HexagonalArchitectureTest.java` in every microservice (ArchUnit)
- JaCoCo minimum coverage gate: 80% on `domain/` and `application/`

## References

- Evans, E. — *Domain-Driven Design* (2003)
- Cockburn, A. — *Hexagonal Architecture* (2005)
- Hombergs, T. — *Get Your Hands Dirty on Clean Architecture* (2019)
