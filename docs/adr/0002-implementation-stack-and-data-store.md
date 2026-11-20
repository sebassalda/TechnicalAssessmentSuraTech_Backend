# ADR-0002: Java 21 Hexagonal Microservices and Independent PostgreSQL Stores

- **Status:** Accepted
- **Date:** 2026-11-20

## Context

The assessment permits Mule 4 as the reference implementation stack and explicitly accepts Java 21 for Part B when justified here. Meridian already has an enterprise integration platform with an API Manager and Mule runtime, while Azure is the primary cloud. Part B requires implemented intake for Quetzal (Mexico) and Andina (Colombia), with external dependencies stubbed; Salesforce writes are designed in Part A but are outside Part B implementation scope.

The solution must verify provider-specific signatures, normalize payloads, enforce idempotency, publish a canonical event, retain accepted notifications durably, support operational lookup, and remain extensible to a fourth provider (FR1–FR2, FR4, FR6–FR7, NFR3, NFR6, NFR9). Atomic notification-plus-outbox persistence and uniqueness constraints are key requirements of the chosen design.

## Options considered

### Option 1 — Mule 4 applications and a relational state store

- Pros: Fits the existing integration platform and reference assessment stack; DataWeave simplifies transformations; established API Manager integration and operational governance.
- Cons: Runtime and licensing constraints may increase cost and hinder local reproducibility; implementation and tests depend on Mule-specific tooling; domain-level state coordination and complex transactional persistence still require deliberate design.

### Option 2 — Java 21 / Spring Boot 3 services and independent PostgreSQL databases (selected)

- Pros: Explicitly allowed for Part B; mature JUnit 5 and Spring testing; clear ports-and-adapters boundaries; strong transaction/unique-key semantics for outbox and idempotency; Docker packaging and independent Azure Container Apps scaling; straightforward local builds with stubbed adapters.
- Cons: Requires the squad to operate additional Azure-hosted applications outside the Mule runtime; more custom integration code; two databases and migrations add cost and maintenance; API Manager policies and Salesforce integration path must be coordinated with the enterprise platform team.

### Option 3 — Java services with a document/NoSQL store

- Pros: Flexible payload and metadata storage; potentially elastic write scaling and partitioning.
- Cons: Cross-record atomicity, uniqueness, per-payment locking and operational status queries become more complicated or store-specific; offers little practical advantage at the documented traffic volume.

## Decision

Implement two **independently deployable Java 21 / Spring Boot 3 services**, packaged as Docker images and hosted on **Azure Container Apps**:

1. **Webhook Intake Service:** provider-specific HTTP adapters and authentication; canonical normalization; idempotency and durable notification-plus-Outbox transaction; asynchronous event publication; operational query API and status projection.
2. **Salesforce Worker Service:** Service Bus subscription consumer; payment-state ordering and per-payment coordination; globally bounded Salesforce calls and collection upserts through the approved enterprise integration path; durable retry/reconciliation and result Outbox.

Use **hexagonal architecture** within each service: inbound HTTP or messaging adapters invoke application use cases; domain logic owns state transition and idempotency rules; outbound ports abstract persistence, messaging, secrets and Salesforce. Provider-specific adapters are isolated from the canonical model. This is an internal modularity decision, not a requirement to split into more services.

Choose **PostgreSQL** for each service's **separate logical database and credentials**. The Intake database contains notification metadata, delivery attempts, unique provider business keys, Outbox rows and operational status projections. The Worker database contains per-payment state and unresolved operation reservations, processed-event keys, durable retry/reconciliation work and result Outbox rows. Enforce uniqueness with database constraints, use short transactional writes, and use row locks or leased reservations to coordinate per-payment processing across replicas. No service may directly query another service's database.

Use **Azure Service Bus topics/subscriptions** for canonical payment events (Salesforce, Finance and Notifications subscriptions) and a separate processing-results topic for the Intake operational projection. Use **Azure Key Vault** and managed identities for Azure resources where supported. External entry points remain behind the existing API Manager; internal operations queries require OIDC/OAuth 2.0 authorization. The Salesforce adapter follows ADR-0001.

For **Part B**, implement the Quetzal and Andina intake adapters and tests, but **stub broker, datastore and secrets adapters** as required by `TASKS.md`. Austral is covered by Part A design but need not be implemented in Part B. A real Salesforce Worker is a future production component and is **not required as a functioning Part B deliverable**.

Use schema migrations and indexed PostgreSQL tables; start without partitioning, adding it only if retention and measured query/write patterns justify it. Initial retention policies are those proposed in `SOLUTION_DESIGN.md` and require legal/privacy approval. The design uses a single Azure region as stated in the scenario; no unrequested multi-region deployment is assumed.

## Consequences

- Positive: Java is explicitly justified for the practical exercise; clear separation of provider-specific and domain logic; reproducible JUnit 5 tests; strong relational guarantees for deduplication and Outbox; independently scalable services; an extensible adapter model.
- Negative: More infrastructure and deployment artifacts than a single Mule application; two database lifecycles and migrations; custom code for broker publishing, rate limiting and operational status projection; team must maintain Spring Boot security and dependency updates.
- Risks and how they are mitigated: Architecture complexity is contained to two services with explicit responsibilities; Azure/Mule interoperability is governed by the existing API Manager; data divergence is avoided through asynchronous result events instead of cross-database reads; state recovery uses database constraints, backups, outbox monitoring and idempotent consumers; Part B avoids untestable cloud dependencies by using interfaces and deterministic stubs.

**References:** `TASKS.md` (Part A, ADR-0002; Part B Java 21 acceptance and stubbing); `SCENARIO.md` (ecosystem, FR1–FR7, NFR3/NFR6/NFR9); `docs/design/SOLUTION_DESIGN.md` (Sections 1, 3–5).
