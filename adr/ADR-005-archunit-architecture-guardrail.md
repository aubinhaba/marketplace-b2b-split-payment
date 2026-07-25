# ADR-005 — ArchUnit as the Architectural Guardrail in CI

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-05-10 |
| **Deciders** | Aubin |
| **Tags** | `archunit`, `architecture`, `ci`, `hexagonal`, `tests` |

## Context

ADR-001 defines strict dependency rules between layers (`infrastructure` → `application` → `domain`).
Such rules are easy to state and easy to erode:

- `@Entity` lands in the domain "just for this one case".
- A controller injects a Spring Data repository directly instead of going through a use case.
- A JPA entity travels across every layer as a de facto DTO.

Without mechanical enforcement, an architectural rule lives in a wiki and in code review — both entirely
human, therefore fallible. A violation merged late on a Friday reaches production before anyone reads it.

## Decision

Enforce the boundaries with **ArchUnit**, as ordinary JUnit tests running in the `test` phase, so a
violation fails the build.

Each service owns its own `HexagonalArchitectureTest` scanning its own root package. A single shared test
in `marketplace-commons` would couple the modules: changing `commons` would then re-run every service's
architecture suite.

Four rules are in place today:

| Rule | What it forbids |
|---|---|
| `domain_must_not_depend_on_frameworks` | any `org.springframework..`, `jakarta.persistence..` or `software.amazon..` reference from `domain/` |
| `domain_must_not_depend_on_infrastructure` | reversing the dependency flow |
| `application_must_not_depend_on_infrastructure` | a use case reaching a concrete adapter instead of a port |
| `no_api_package_at_root` | a generic `api/` package — controllers are inbound adapters under `infrastructure.adapter.in.rest` |

Each rule carries a `.because(...)` clause naming the ADR it enforces, so a failing build explains why the
rule exists rather than only what it blocked.

The first rule is the one that matters most: it is what keeps the domain testable with plain JUnit, and it
is the reason `Order` is mapped to a separate `OrderJpaEntity` instead of being annotated directly.

## Alternatives considered

**Code review only** — rejected. Catches violations only when someone happens to look, with an efficacy
that depends on reviewer availability.

**Checkstyle / SpotBugs / PMD** — complementary, not sufficient. They analyse syntax and local patterns;
they do not model dependency relationships between packages. Worth having, not a substitute.

**Spring Modulith module verification** — not applicable. It targets modular monoliths, whereas this
platform is a set of independently deployed services.

## Consequences

**Benefits**
- The architecture is machine-verified rather than dependent on discipline.
- The rules are living documentation: they cannot drift from the code without failing.
- Fast — no Spring context, no containers; the suite runs in seconds and can be run constantly.

**Costs**
- An over-strict rule can block a legitimate case; the fix is an explicit, commented exemption rather
  than deleting the rule.
- Rules must be updated when the package structure changes.
- ArchUnit reads compiled bytecode, so it is the last line of defence, not the only one: it cannot run at
  all if the code does not compile.

**Planned extensions** — a rule requiring `@Audited` on every business JPA entity arrives with the audit
trail work, and one forbidding direct `SqsTemplate` use outside the outbox adapter tightens ADR-003.

## Enforcement

Self-enforcing: `mvn test` runs the rules and fails the build on violation.

## References

- ArchUnit user guide — https://www.archunit.org/userguide/html/000_Index.html
- Hombergs, T. — *Enforcing Architecture with ArchUnit* — https://reflectoring.io/enforcing-architecture-with-archunit/
