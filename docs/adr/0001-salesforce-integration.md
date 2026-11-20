# ADR-0001: Asynchronous, Batched Salesforce Integration Through the Enterprise Integration Path

- **Status:** Accepted
- **Date:** 2026-11-20

## Context

Meridian Mutual must record premium payments in its single Salesforce org as `Premium_Payment__c`, linked to `Policy__c` through `Policy__r.Policy_Number__c` (FR3), without directly changing policy status. Duplicate or out-of-order events can incorrectly trigger Salesforce policy automation and affect coverage (FR4–FR5).

Salesforce availability is approximately 97%, with outages commonly lasting 5–90 minutes. The Payments Integration allocation is 150,000 REST calls per rolling 24 hours within an org-wide shared limit, and at most 25 concurrent requests. Individual upserts have p95 latency around 1.2 seconds; collection upserts support up to 200 records per REST call, with p95 latency around 4 seconds. Month-end traffic can reach 120,000 notifications/day and bursts of 40/s. Providers require prompt webhook acknowledgements, and accepted notifications cannot be lost (NFR1–NFR5).

Meridian's enterprise integration platform requires APIs to be published through the existing API Manager; systems must not be connected point to point. The platform can front Azure-hosted services and run Mule 4 integration applications.

## Options considered

### Option 1 — Synchronous Salesforce upsert in the webhook request

- Pros: Simple linear request path; immediate downstream result on success; no broker or background Worker required.
- Cons: Salesforce outages and latency cause provider timeouts; couples acceptance to Salesforce availability; retry storms and duplicate writes; poor isolation from the shared API budget; cannot reliably satisfy NFR3 and NFR4 together.

### Option 2 — Asynchronous Worker with individual Salesforce upserts

- Pros: Durable decoupling from webhook acceptance; simple per-record error handling; stable external-ID upserts support idempotency.
- Cons: At month-end, approximately 116,400 unique events/day after an assumed 3% duplicate rate could consume nearly the entire integration allocation if each requires a write, before reconciliation and retries; weaker API headroom.

### Option 3 — Asynchronous Worker with bounded collection upserts and enterprise-mediated access (selected)

- Pros: Durable acceptance independent of Salesforce; controlled catch-up after outages; batching reduces call usage; per-record results supported with `allOrNone: false`; central rate/concurrency controls; consistent enterprise trust boundaries.
- Cons: Eventual consistency; more moving parts; batch composition and per-record partial failures are complex; additional operational cost; gateway availability and latency become dependencies.

## Decision

Use a separate **Salesforce Worker Service**, consuming canonical payment events from an Azure Service Bus subscription. The Worker integrates with Salesforce **through Meridian's approved enterprise API Manager/integration path**, not a direct point-to-point connection. The precise gateway deployment/routing configuration is an enterprise dependency, not a third microservice owned by the Payments squad.

The Worker uses the Salesforce REST collection upsert endpoint for `Premium_Payment__c`, keyed by unique external ID `Payment_Key__c`. Choose an **initial target of 100 records per batch**, **maximum 2-second flush wait**, and **hard endpoint limit of 200 records**. Flush smaller batches when needed to protect freshness. Use `allOrNone: false`, persist the outcome of each record independently, and retry only unresolved or failed records as appropriate. Batch only records eligible under the per-payment ordering rules; never submit conflicting transitions of the same payment concurrently.

Set an initial **globally coordinated limit of 10 concurrent Salesforce requests**, below the documented maximum of 25. Track consumption against the **150,000-call rolling 24-hour integration budget**, allowing an initial 20% headroom for retries, reconciliation and operational requests, and respond to org-wide `REQUEST_LIMIT_EXCEEDED` by deferring writes until capacity recovers. Coordinate throttling across all Worker replicas rather than relying on per-instance counters. Cache OAuth 2.0 client-credentials tokens (about two-hour validity); keep the client secret in Azure Key Vault.

Persist retry and reconciliation work before acknowledging the original broker delivery. Retry transient `5xx`, timeouts and `UNABLE_TO_LOCK_ROW` with bounded exponential backoff and jitter; refresh invalid tokens; defer on exhausted quotas; quarantine permanent validation errors. For ambiguous remote outcomes, read Salesforce by `Payment_Key__c` and reconcile before replaying an upsert. Serialize writes by payment and retain an unresolved-operation reservation so older timed-out writes cannot be blindly replayed after newer states. The Salesforce REST upsert itself is not a conditional compare-and-set operation; strict protection against externally initiated concurrent writes would require additional Salesforce-side enforcement.

**Capacity rationale:** at 120,000 deliveries/day with 3% duplicates, the illustrative unique volume is 116,400/day. Perfectly filled 100-record batches would use approximately 1,164 write calls/day, excluding reconciliation, failures and other API traffic. With a two-second flush interval, actual batch occupancy may be much lower; this figure is a lower-bound illustration, not a throughput guarantee. The design must be load-tested for 40/s bursts, month-end volume, 5–90-minute outages, and API exhaustion.

The solution targets 95% Salesforce visibility within five minutes and 99.5% within one hour, measured from notification receipt. A complete 90-minute Salesforce outage makes the one-hour target unattainable for affected notifications; record and report this dependency limitation rather than claiming an absolute guarantee.

## Consequences

- Positive: Provider responses remain fast and durable; Salesforce outages do not cause loss of accepted notifications; collection upserts conserve the shared API allocation; payment status and policy automation remain in Salesforce; independent scaling of intake and Worker.
- Negative: Additional queue, database, Worker and gateway operational burden; temporary lag between acceptance and Salesforce visibility; more complex batching, replay and partial-result handling.
- Risks and how they are mitigated: Duplicate effects are mitigated by stable external IDs and event deduplication; stale state by per-payment serialization, timestamp/state checks and reconciliation; unknown outcomes by Salesforce reads before retry; API starvation by globally coordinated quota and concurrency controls; gateway dependency by durable queues, backoff and monitoring; SLO misses during Salesforce outages by explicit reporting and backlog recovery tests.

**References:** `TASKS.md` (Part A, ADR-0001); `SCENARIO.md` (FR3–FR5, NFR1–NFR5); `reference/salesforce/API_CONSTRAINTS.md`; `reference/salesforce/DATA_MODEL.md`; `docs/design/SOLUTION_DESIGN.md` (Sections 1–2, 4–6).
