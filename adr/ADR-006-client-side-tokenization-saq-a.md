# ADR-006 — Client-Side Tokenization to Stay in PCI DSS SAQ-A Scope

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-05-10 |
| **Deciders** | Aubin |
| **Tags** | `pci-dss`, `security`, `tokenization`, `stripe-elements`, `saq-a`, `compliance` |

## Context

The platform takes card payments. Card data — PAN, CVV, expiry — is the most sensitive data in the
system, and PCI DSS decides how much of the standard applies based on whether that data ever touches our
infrastructure. The self-assessment questionnaire that applies is what actually sets the cost:

| SAQ | Applies to | Controls | External audit |
|---|---|---|---|
| **SAQ-A** | Platform that never touches card data | ~22 | No, self-assessed |
| SAQ-A-EP | Platform serving the JavaScript of the payment page | ~191 | No |
| SAQ-D | Platform that stores, processes or transmits card data | 341 | Yes, annual QSA |

The gap between SAQ-A and SAQ-D is months of audit work, tens of thousands of euros a year, and material
changes to the infrastructure. The architecture decides which one applies.

## Decision

Collect card data exclusively through **client-side tokenization** with Stripe Elements, so the platform
stays in **SAQ-A** scope.

The card fields live in an iframe served by Stripe. The PAN goes from the buyer's browser straight to
Stripe over HTTPS and never transits our network. Stripe returns an opaque, single-use token (`pm_…`),
and that token is all our API ever receives:

```
Browser                                     Our backend
  Stripe Elements iframe
    card number, expiry, CVV  ──HTTPS──▶  Stripe
                              ◀──────────  pm_1MqLiJLkdIwHu7ix
    POST /api/v1/payments { paymentMethodId: "pm_…" }  ──▶  payment-service
                                                             PaymentIntent.create(pm_…)
```

The invariant this buys: **no PAN in any request body, log line, database column, SQS message, outbox
payload or S3 file we own.**

## Alternatives considered

**Collect card data server-side and encrypt it** — rejected. Even with AES-256 at rest, receiving a PAN
puts the platform in SAQ-D. Encrypting stored PANs (requirement 3.4) is the easy part; the expensive
requirements are 6 (secure development), 10 (log every access to cardholder data) and 11 (penetration
testing and quarterly scans).

**PSP-hosted payment page** — valid, and it yields the same SAQ-A scope, but it redirects the buyer out
of our interface. On a B2B marketplace where checkout UX is part of the product, an inline iframe wins.

**Network tokens (EMVCo)** — complementary rather than alternative. They replace the PAN for recurring
charges and are worth revisiting when subscriptions land; they do not change the scope decision here.

## Consequences

**Benefits**
- SAQ-A: self-assessment, no external QSA audit.
- Card data liability sits with the PSP.
- The security perimeter covers business data only — amounts, identifiers, tokens.

**Costs**
- A hard dependency on the PSP's JavaScript: if their CDN is down, checkout is down. The hosted payment
  page is the fallback.
- Limited control over the card field UI. Elements is themable, not arbitrary.
- No server-side card storage. Recurring charges rely on `PaymentMethod` objects held by Stripe against a
  Customer, referenced by token.

## Enforcement

Scope is an architectural property, so it has to be checked rather than assumed:

- No field, parameter or DTO in the codebase names a card attribute. `CreatePaymentRequest` carries
  identifiers and an amount, nothing else.
- `StripeGatewayAdapter` wraps `StripeException` messages into its own error string; card data is not
  part of Stripe's error payloads at this level, and the adapter does not log the exception body.
- Still open: a log-scrubbing filter that masks anything shaped like a PAN, as defence in depth against a
  future code path that logs a request body. Tracked for the hardening sprint.

## References

- PCI SSC — *SAQ A v4.0* — https://www.pcisecuritystandards.org/document_library/
- Stripe — *PCI compliance guide* — https://stripe.com/docs/security/guide
