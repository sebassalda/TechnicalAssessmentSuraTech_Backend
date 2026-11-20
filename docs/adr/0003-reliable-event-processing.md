# ADR-0003: Transactional Outbox and At-Least-Once Messaging with Idempotent, Ordered Processing

- **Status:** Accepted
- **Date:** 2026-11-20

## Context

A webhook is considered accepted once Meridian returns HTTP `2xx`; no accepted notification may be lost (NFR3). Providers retry notifications and may deliver events late or out of order. The payment's latest real state must be reflected in Salesforce, and a duplicate or stale write could trigger policy reinstatement or suspension incorrectly (FR4–FR5). Canonical events must also reach independent Finance and Notifications consumers (FR6), and Operations must see the processing outcome (FR7).

A database commit, Azure Service Bus publish, broker acknowledgement and Salesforce REST write cannot be committed in one distributed transaction. A truthful reliability model must account for crashes and ambiguous results at every boundary.

## Options considered

### Option 1 — Publish to Service Bus directly during the HTTP request

- Pros: Few components; immediate message publication; no Outbox polling.
- Cons: A successful database commit and failed publish can lose the event; successful publish followed by a failed HTTP response may duplicate delivery; broker unavailability threatens provider timeout and durable acceptance.

### Option 2 — Database transaction plus Transactional Outbox and at-least-once messaging (selected)

- Pros: Accepted notification and outgoing canonical event are committed atomically; publication survives broker outages; supports independent subscriptions; local database uniqueness and recoverable replay; no cross-system distributed transaction needed.
- Cons: Duplicate broker publication remains possible; eventual consistency; extra tables, polling, retries, monitoring and cleanup; consumer-side deduplication is mandatory.

### Option 3 — Rely solely on broker duplicate detection or message ordering

- Pros: Broker features can reduce duplicate deliveries and help sequence messages.
- Cons: Broker duplicate detection does not cover provider semantic retries or Salesforce effects; message arrival order may differ from business event order; cannot atomically bind local notification acceptance to broker publication; does not solve ambiguous remote writes.

## Decision

Use the **Transactional Outbox pattern in the Webhook Intake Service**. After provider-specific authentication and validation, insert the notification record, its unique provider business key and the canonical Outbox event **in one PostgreSQL transaction**. Return a provider-compatible successful HTTP response only after the commit. A duplicate business event is acknowledged without generating another Outbox event; a conflicting duplicate is quarantined. On database failure, do not acknowledge acceptance.

Publish pending Outbox rows to the **Azure Service Bus payment-events topic** using a recoverable publisher. Mark each row published only after broker acknowledgement. A crash between publish and marking can cause duplicate messages; all consumers must handle **at-least-once delivery**. Finance and Notifications use their own subscriptions; the Payments squad does not implement their consumers.

Use these **provider-specific event deduplication keys**:

| Provider | Event idempotency key | Stable payment key |
|---|---|---|
| Quetzal (MX) | `Quetzal:{id}` | `MX:{data.charge.id}` |
| Andina (CO) | `Andina:{transaccion.id}:{transaccion.estado}` | `CO:{transaccion.id}` |
| Austral (CL) | `Austral:{eventId}` | `CL:{payment.paymentId}` |

Andina's `notificacion_id` changes on delivery retries and must not be the sole deduplication key. Its `(transaction ID, state)` strategy assumes at most one genuine transition to a given state per transaction; conflicting repeats require reconciliation, as documented in `docs/ASSUMPTIONS.md`.

The **Salesforce Worker** records `(consumer_name, canonical_event_id)` uniquely and serializes state changes **per payment key** across replicas. Compare authoritative provider occurrence times and valid state transitions, not Service Bus arrival order. A late `Captured` event must not overwrite a newer `Refunded` state. Equal timestamps with conflicting states, uncertain writes and invalid transitions enter reconciliation. Persist a durable in-flight operation reservation before the remote write and do not permit a later write to overtake an unresolved earlier one. Salesforce's external-ID upsert prevents duplicate payment records but **does not itself enforce state ordering**; remote reconciliation is required after ambiguous outcomes.

Use a **Worker result Outbox** for processing outcomes, published to a separate Service Bus topic. The Intake status consumer updates its local operational projection idempotently. For a deferred Worker message, persist recoverable retry/reconciliation work and its next attempt **before** completing the original Service Bus delivery. Do not rely on keeping broker message locks open during a long Salesforce outage. Poison events and permanent failures enter a durable remediation state or dead-letter queue; replay preserves original identifiers and rechecks ordering/idempotency. Keep unresolved records until remediation and retain deduplication records for the entire supported replay window.

The outcome is **at-least-once transport with effectively-once business effects under the stated idempotency and ordering controls**, not global exactly-once delivery. No distributed transaction between PostgreSQL, Service Bus and Salesforce is claimed. Provider-specific webhook acknowledgement codes and timeouts are defined in `SOLUTION_DESIGN.md` and their webhook specifications.

## Consequences

- Positive: Durable acceptance even when Service Bus or Salesforce is unavailable; safe independent downstream consumption; recoverable publication; operational lookup; controlled handling of duplicate and stale events.
- Negative: Additional PostgreSQL writes and Outbox dispatchers; delayed event visibility; duplicate transport messages and more complicated replay/reconciliation; storage and monitoring overhead.
- Risks and how they are mitigated: Publisher crashes cause duplicate messages, mitigated by consumer deduplication; uncertain Salesforce results require reconciliation before replay; per-payment races require durable reservations and serialized writes; DLQ accumulation requires alerting and audited replay; retention must not delete deduplication keys while corresponding events remain replayable; strict remote compare-and-set would require Salesforce-side conditional enforcement beyond the provided data model.

**References:** `TASKS.md` (optional ADR-0003, FR4–FR7); `SCENARIO.md` (NFR3 and provider retries); `reference/providers/*/WEBHOOK_SPEC.md`; `reference/salesforce/DATA_MODEL.md`; `docs/design/SOLUTION_DESIGN.md` (Sections 2.3–2.5, 4–5).
