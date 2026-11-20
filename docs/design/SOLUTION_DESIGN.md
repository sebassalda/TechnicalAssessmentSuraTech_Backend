# Solution Design — Premium Payment Notifications

## 1. System Context and Architecture Overview

```mermaid
flowchart TB
    subgraph EXTERNAL["External Trust Boundary"]
        MX["Quetzal Pagos - Mexico"]
        CO["Andina Pay - Colombia"]
        CL["Austral Pagos - Chile"]
    end

    subgraph MERIDIAN["Meridian Enterprise Trust Boundary"]
        APIM["Existing API Manager"]
        IDP["Existing OAuth2 / OIDC Identity Provider"]
        USERS["Authorized Operations Users"]

        subgraph AZURE["Azure Workload Trust Boundary"]
            subgraph INTAKE["Webhook Intake Service - Azure Container Apps"]
                WEBHOOK["Provider Webhook Adapters"]
                VALIDATOR["Signature Validation and Normalization"]
                INTAKE_OUTBOX["Transactional Outbox"]
                INTAKE_PUBLISHER["Outbox Publisher"]
                STATUS_CONSUMER["Processing Status Consumer"]
                OPS_API["Operations Query API"]
            end

            INTAKE_DB[("PostgreSQL - Intake Database")]

            PAYMENT_BUS[("Azure Service Bus - Payment Events Topic")]
            RESULT_BUS[("Azure Service Bus - Processing Results Topic")]

            subgraph WORKER["Salesforce Worker Service - Azure Container Apps"]
                EVENT_CONSUMER["Payment Event Consumer"]
                PROCESSOR["Ordering, Idempotency and Batching"]
                SF_CLIENT["Salesforce Integration Adapter"]
                WORKER_PUBLISHER["Result Outbox Publisher"]
            end

            WORKER_DB[("PostgreSQL - Worker Database")]

            KV["Azure Key Vault"]
            MON["Azure Monitor / Application Insights"]
        end

        GATEWAY["Approved Enterprise Integration Gateway"]
        SF["Salesforce - System of Record"]
        FIN["Finance Consumer"]
        NOTIF["Notifications Consumer"]
    end

    MX -->|"HTTPS Webhook"| APIM
    CO -->|"HTTPS Webhook"| APIM
    CL -->|"HTTPS Webhook"| APIM

    APIM -->|"Route Webhooks"| WEBHOOK
    WEBHOOK --> VALIDATOR
    VALIDATOR --> INTAKE_OUTBOX

    INTAKE_OUTBOX -->|"Atomic Database Transaction"| INTAKE_DB
    INTAKE_DB -->|"Read Pending Events"| INTAKE_PUBLISHER
    INTAKE_PUBLISHER -->|"Publish Canonical Events"| PAYMENT_BUS

    PAYMENT_BUS -->|"Salesforce Subscription"| EVENT_CONSUMER
    EVENT_CONSUMER --> PROCESSOR
    PROCESSOR <-->|"Processing State and Results"| WORKER_DB
    PROCESSOR --> SF_CLIENT
    SF_CLIENT -->|"Authorized Integration Request"| GATEWAY
    GATEWAY -->|"Salesforce REST Upsert / OAuth2"| SF

    PAYMENT_BUS -->|"Finance Subscription"| FIN
    PAYMENT_BUS -->|"Notifications Subscription"| NOTIF

    WORKER_DB -->|"Read Pending Result Events"| WORKER_PUBLISHER
    WORKER_PUBLISHER -->|"Publish Processing Outcomes"| RESULT_BUS
    RESULT_BUS -->|"Processing Results Subscription"| STATUS_CONSUMER
    STATUS_CONSUMER -->|"Update Operational Projection"| INTAKE_DB

    USERS -->|"Authenticated Request"| APIM
    IDP -.->|"OAuth2 Token Validation"| APIM
    APIM -->|"Route Operational Query"| OPS_API
    OPS_API -->|"Read Notification Status"| INTAKE_DB

    KV -.->|"Secrets and Configuration"| INTAKE
    KV -.->|"Salesforce Credentials"| WORKER

    INTAKE -.->|"Metrics, Logs and Traces"| MON
    WORKER -.->|"Metrics, Logs and Traces"| MON
```

### 1.1 Microservice Boundaries and Data Ownership

The proposed solution adopts an event-driven microservices architecture based on Java 21, Spring Boot 3, Hexagonal Architecture, and Azure-managed services.

The architecture consists of two independently deployable and scalable microservices, each with clearly defined responsibilities and independent data ownership.

**Webhook Intake Service**

The Webhook Intake Service is responsible for receiving payment notifications from Quetzal Pagos (Mexico), Andina Pay (Colombia), and Austral Pagos (Chile).

Its main responsibilities include:

- Validating provider-specific webhook signatures and request timestamps.
- Transforming provider payloads into a versioned canonical payment event.
- Enforcing notification idempotency using provider-specific business keys.
- Persisting accepted notifications and outgoing events atomically using the Transactional Outbox pattern.
- Publishing canonical payment events asynchronously to Azure Service Bus.
- Exposing secured operational endpoints for tracking notification processing status.

The Outbox Publisher operates as an internal component of the Webhook Intake Service. It retrieves pending events from PostgreSQL, publishes them to Azure Service Bus, and records publication outcomes.

A successful webhook response is returned only after the notification and its corresponding Outbox event have been durably committed.

**Salesforce Worker Service**

The Salesforce Worker Service is responsible for consuming canonical payment events and integrating them with Salesforce.

Its main responsibilities include:

- Consuming payment events from a dedicated Azure Service Bus subscription.
- Enforcing idempotent processing and payment-state ordering rules.
- Preventing outdated notifications from overwriting newer payment states.
- Grouping compatible payment records into batches to optimize Salesforce API consumption.
- Performing idempotent Salesforce REST upserts using stable external payment keys.
- Managing transient failures, retries, dead-letter handling, and processing outcomes.
- Persisting processing results and publishing operational status events through a recoverable Outbox mechanism.

Salesforce remains the system of record for payment and policy information. The integration updates payment records only; policy reinstatement is handled by existing Salesforce automation. For Quetzal partial refunds, the canonical event retains the refunded amount; the final Salesforce status and automation behavior require a documented business rule because the supplied Salesforce status model does not distinguish partial from full refunds.

**Data Ownership and Operational Visibility**

Each microservice owns its persistence model. Direct database access between services is prohibited to prevent tight coupling and preserve independent evolution.

The Webhook Intake Service maintains notification records, idempotency keys, Outbox events, and an operational status projection.

The Salesforce Worker Service maintains payment-processing state, Salesforce integration outcomes, and pending processing-result events.

Operational visibility is implemented through asynchronous processing-result events. A dedicated Processing Status Consumer updates the operational projection maintained by the Webhook Intake Service.

This enables authorized Operations users to track notifications throughout their lifecycle without accessing the Salesforce Worker database or searching application logs.

Because Salesforce REST operations and local PostgreSQL transactions cannot be committed atomically, the Worker uses stable external payment keys, idempotent upserts, and reconciliation procedures to recover from uncertain outcomes.

### 1.2 Deployment and Scalability

Both microservices are packaged as independent Docker images and designed for deployment on Azure Container Apps.

This approach provides independent deployment, horizontal scaling, and managed container execution without requiring a dedicated Kubernetes cluster.

The Webhook Intake Service scales according to incoming HTTP traffic and webhook processing demand.

The Salesforce Worker Service scales according to Azure Service Bus message backlog, processing latency, and Salesforce integration capacity.

Azure Service Bus decouples notification ingestion from downstream processing through a payment-events topic and independent subscriptions for Salesforce, Finance, and Notifications.

A separate processing-results topic communicates Salesforce processing outcomes back to the operational status projection.

The Salesforce Worker applies coordinated rate limiting and concurrency controls to respect Salesforce's maximum of 25 concurrent integration requests and its allocated budget of 150,000 API calls per rolling 24 hours.

The design accommodates temporary Salesforce unavailability by retaining pending messages for asynchronous processing and applying controlled retries.

PostgreSQL provides transactional consistency, unique constraints, indexing, and durable storage for notification and processing state. Each microservice maintains separate logical databases and access credentials.

The architecture supports future payment providers by introducing new provider-specific adapters without modifying the canonical payment-processing domain or requiring changes to downstream consumers.

### 1.3 Trust Boundaries and Security

The architecture defines three primary trust boundaries:

**External Provider Boundary**

Payment providers communicate with Meridian through HTTPS webhooks exposed by the existing API Manager.

Each provider adapter verifies the HMAC signature over the exact raw request bytes using the documented provider-specific encoding and signed input. Quetzal uses a signed Unix timestamp with a mandatory 300-second tolerance and supports multiple signatures during secret rotation; Andina signs its ISO-8601 timestamp and raw body and the design enforces its recommended 10-minute tolerance; Austral signs only the raw body and uses durable event-ID deduplication for replay defense. All signatures are compared in constant time. Required request fields and supported business events are validated before acceptance.

Invalid or unauthenticated notifications are rejected without producing canonical payment events.

**Meridian Enterprise Boundary**

The existing API Manager provides the controlled entry point for externally exposed APIs and authorized operational queries.

Operational endpoints require OAuth 2.0 authentication and role-based authorization through Meridian's existing identity provider.

**Azure Workload Boundary**

The Webhook Intake Service and Salesforce Worker Service operate within the Azure workload environment.

Azure managed identities are used where supported to authenticate against Azure resources, including Service Bus and Key Vault, following the principle of least privilege.

Salesforce authentication uses OAuth 2.0 client credentials. Credentials are securely managed through Azure Key Vault, and access tokens are cached and reused according to their validity.

Application telemetry is centralized through Azure Monitor and Application Insights.

Sensitive payment information, authentication secrets, and personally identifiable information must not be exposed through application logs, traces, or operational responses.

All inter-service messages use versioned contracts, and access to messaging subscriptions and databases is restricted to authorized components.

## 2. End-to-End Payment Notification Processing

### 2.1 Sequence Diagram

```mermaid

sequenceDiagram
    autonumber

    participant P as Payment Provider
    participant A as Enterprise API Manager
    participant I as Webhook Intake Service
    participant D1 as Intake PostgreSQL
    participant O as Intake Outbox Publisher
    participant B as Azure Service Bus
    participant W as Salesforce Worker
    participant D2 as Worker PostgreSQL
    participant G as Approved Integration Gateway
    participant S as Salesforce
    participant R as Processing Results Topic
    participant C as Status Consumer

    P->>A: HTTPS payment notification
    A->>I: Forward raw request and signature
    I->>I: Verify provider-specific authenticity

    alt Invalid signature
        I-->>A: Authentication failure
        A-->>P: Reject webhook
    else Invalid payload
        I-->>A: Validation error
        A-->>P: Reject malformed notification
    else Authenticated and valid
        I->>I: Normalize canonical payment event
        I->>D1: Begin transaction
        I->>D1: Insert notification with unique event key

        alt Duplicate notification
            D1-->>I: Existing notification detected
            I->>D1: End transaction without new event
            I-->>A: Idempotent acknowledgement
            A-->>P: Provider-compatible 2xx
        else New notification
            I->>D1: Insert canonical event into Outbox
            I->>D1: Commit transaction

            alt Persistence failure
                I-->>A: Retryable server error
                A-->>P: Non-2xx response
            else Durable commit successful
                I-->>A: Accepted acknowledgement
                A-->>P: Provider-compatible 2xx
            end
        end
    end

    opt Pending Intake Outbox event
        O->>D1: Read pending event
        O->>B: Publish canonical event

        alt Broker unavailable
            O->>D1: Retain event for retry
        else Broker acknowledges
            B-->>O: Publication acknowledged
            O->>D1: Mark event published
        end
    end

    opt Canonical event available
        B->>W: Deliver Salesforce subscription message
        W->>D2: Check event and payment processing state

        alt Event already completed
            W->>B: Complete duplicate delivery
        else Event is stale
            W->>D2: Persist skipped outcome and result Outbox
            W->>B: Complete message
        else Event requires processing
            W->>W: Enforce ordering and concurrency
            W->>W: Prepare Salesforce upsert batch

            alt API budget or concurrency unavailable
                W->>D2: Persist deferred processing state
                W->>W: Arrange durable retry
            else Salesforce capacity available
                W->>G: Authorized integration request
                G->>S: REST upsert Premium_Payment__c

                alt Upsert successful
                    S-->>G: Success response
                    G-->>W: Upsert result
                    W->>D2: Persist success and result Outbox
                    W->>B: Complete message
                else Salesforce unavailable
                    S-->>G: 5xx or timeout
                    G-->>W: Transient or uncertain failure
                    W->>D2: Persist retry or reconciliation state
                    W->>W: Arrange durable retry
                else API limit exceeded
                    S-->>G: 403 REQUEST_LIMIT_EXCEEDED
                    G-->>W: Rate limit failure
                    W->>D2: Persist deferred processing state
                    W->>W: Retry after capacity recovers
                else Permanent validation failure
                    S-->>G: Validation error
                    G-->>W: Non-retryable failure
                    W->>D2: Persist failure and result Outbox
                    W->>W: Route to DLQ or remediation
                end
            end

            Note over W,B: Complete original message only after durable recovery is secured
        end
    end

    opt Pending Worker result Outbox event
        W->>D2: Read pending processing outcome
        W->>R: Publish result event

        alt Result publication fails
            W->>D2: Retain result event for retry
        else Publication succeeds
            R-->>W: Publication acknowledged
            W->>D2: Mark result event published
            R->>C: Deliver processing result
            C->>D1: Idempotently update operational status
            C->>R: Complete result message
        end
    end
```

### 2.2 Notification Processing and Reliability Guarantees

The proposed payment notification workflow separates synchronous webhook acceptance from asynchronous Salesforce processing. This design prioritizes durability, idempotency, operational traceability, and controlled downstream integration.

**Webhook Authentication and Validation**

Payment providers submit HTTPS notifications through Meridian's existing API Manager.

The Webhook Intake Service selects the appropriate provider adapter and validates each request according to the provider's documented authentication scheme, payload format, and applicable replay-protection requirements.

Invalid signatures and malformed notifications are rejected before any canonical payment event is published.

The service preserves the original request bytes required for signature verification and applies provider-specific validation before transforming the notification.

**Canonical Event Generation and Durable Acceptance**

After successful validation, the Webhook Intake Service transforms the provider notification into a versioned canonical payment event.

The service derives a provider-specific idempotency key and starts a PostgreSQL transaction.

Within the same transaction, it persists the accepted notification and its corresponding Outbox event.

A successful HTTP acknowledgement is returned only after the transaction commits.

Duplicate notifications are identified through unique database constraints and acknowledged without producing additional canonical events.

If persistence fails, the service returns a non-success response so that the provider can retry according to its documented delivery policy.

Response codes and timing must comply with each provider's webhook specification.

**Asynchronous Event Publication**

The Intake Outbox Publisher retrieves pending canonical events from PostgreSQL and publishes them to an Azure Service Bus topic.

Independent subscriptions allow the Salesforce Worker, Finance, and Notifications consumers to process events without coupling their availability or execution schedules.

If publication fails, the event remains stored in PostgreSQL and is retried.

If publication succeeds but the publisher fails before marking the event as delivered, duplicate publication is possible.

Consumers must therefore support at-least-once delivery and idempotent processing.

**Salesforce Processing and Ordering**

The Salesforce Worker consumes canonical events from its dedicated Service Bus subscription.

Before performing an integration operation, it checks the event identifier, existing processing outcome, and the latest known payment state.

The Worker applies provider-specific ordering and reconciliation rules to prevent delayed notifications from overwriting newer payment states.

Events that have already been processed are acknowledged without repeating the business operation.

Stale events are recorded as skipped and do not modify Salesforce.

Eligible events are grouped into compatible batches when appropriate to reduce Salesforce API consumption.

The Worker performs authorized integration requests through Meridian's approved enterprise integration path.

Payment records are created or updated in Salesforce using stable external identifiers and idempotent upsert operations.

The integration updates `Premium_Payment__c` records only. Policy reinstatement remains the responsibility of existing Salesforce automation.

**Salesforce Availability and API Protection**

Salesforce availability is approximately 97% per month, with documented outage windows typically ranging from 5 to 90 minutes.

The Salesforce Worker uses asynchronous processing, controlled concurrency, batching, and durable retries to tolerate temporary outages.

The integration must respect a maximum of 25 concurrent Salesforce requests and a budget of 150,000 API calls per rolling 24-hour period.

The Worker also accounts for the shared Salesforce organization-wide API limit.

When Salesforce returns `REQUEST_LIMIT_EXCEEDED`, processing is deferred until API capacity becomes available.

Transient server failures, connection errors, and record-locking conflicts are retried using bounded exponential backoff with jitter.

Permanent validation errors are not repeatedly submitted without correction.

Expired Salesforce access tokens are refreshed according to the supported OAuth 2.0 client credentials flow, and valid tokens are cached for reuse.

**Dead-Letter Handling and Safe Recovery**

Messages that cannot be processed after the configured retry policy are placed in a dead-letter queue or durable remediation state.

Dead-letter records preserve the event identifier, correlation identifier, failure classification, retry history, and operational status.

Reprocessing must preserve the original business identifiers and idempotency guarantees.

Before replaying an uncertain Salesforce operation, the Worker reconciles the payment against Salesforce using its stable external identifier.

A message is acknowledged to Azure Service Bus only after successful processing or after responsibility for its recovery has been durably transferred to a retry, reconciliation, or dead-letter mechanism.

**Operational Status and Traceability**

The Salesforce Worker persists processing outcomes and corresponding result Outbox events in its PostgreSQL database.

A recoverable publisher sends these outcomes to a dedicated Azure Service Bus topic.

The Processing Status Consumer updates the operational projection maintained by the Webhook Intake Service.

Authorized Operations users can search notifications using provider identifiers and inspect their processing status without querying raw application logs.

The operational model distinguishes notification acceptance, publication, Salesforce recording, processing failures, and dead-lettered outcomes.

A correlation identifier is propagated across the HTTP request, canonical event, messaging infrastructure, Salesforce processing, and operational status events.

Sensitive payer information and authentication credentials are excluded from application logs and telemetry.

**Delivery and Freshness Guarantees**

The architecture provides at-least-once event delivery with idempotent business processing.

Unique constraints, stable identifiers, ordered state transitions, recoverable Outbox publication, and Salesforce external-ID upserts prevent duplicate business effects.

No distributed transaction is assumed between PostgreSQL, Azure Service Bus, and Salesforce.

The system is designed to target the required freshness objectives:

- At least 95% of payments visible in Salesforce within 5 minutes of the provider notification.
- At least 99.5% visible within 1 hour.

These objectives must be validated through capacity calculations, queue-lag monitoring, end-to-end latency measurements, and failure-recovery testing.

The design must also ensure that no notification acknowledged with a successful HTTP response is lost, including during broker or Salesforce outages.

### 2.3 Idempotency Strategy

Idempotency is enforced at three boundaries: webhook intake, Service Bus consumption, and Salesforce persistence. Transport delivery remains at least once; the objective is to prevent duplicate business effects.

| Provider | Notification idempotency key | Stable payment key (`Payment_Key__c`) | Rationale |
|---|---|---|---|
| Quetzal (MX) | `Quetzal:{id}` | `MX:{data.charge.id}` | `id` remains stable across delivery retries, while multiple event types can affect one charge. |
| Andina (CO) | `Andina:{transaccion.id}:{transaccion.estado}` | `CO:{transaccion.id}` | `notificacion_id` changes on each retry. Transaction ID and state identify the same business event across retries. |
| Austral (CL) | `Austral:{eventId}` | `CL:{payment.paymentId}` | `eventId` is stable across retries; multiple events may affect one payment. |

The Intake database has a unique constraint on `(provider, notification_business_key)` and stores the original delivery identifier separately for operational lookup. The notification record and canonical Outbox event are inserted in the same transaction. An identical duplicate returns the provider-compatible success response without another Outbox event. If a repeated key carries conflicting business content, the request is quarantined for investigation rather than silently discarded.

The Worker enforces a unique `(consumer_name, canonical_event_id)` processing key and serializes state transitions for the same payment key. `Premium_Payment__c.Payment_Key__c` is a Salesforce unique external ID and is used for upserts. The Worker must reconcile uncertain responses before retrying, because a Salesforce write can succeed even if the HTTP response is lost. Stable payment keys alone do not prevent a stale update; ordering checks are also required.

For Andina, the documented `(transaction ID, state)` key cannot distinguish two genuine occurrences of the same state. The design assumes one business transition per state per transaction; conflicting repeats are quarantined and reconciled with the provider rather than treated as independent transitions. This assumption must be recorded in `docs/ASSUMPTIONS.md`.

### 2.4 Event Ordering and State Resolution

Events are ordered **per payment**, not globally. The Worker uses the provider payment key as the serialization key and persists the last accepted state, provider occurrence timestamp, canonical event ID, and Salesforce synchronization status. Processing for the same payment must be serialized across Worker replicas using a database lock or equivalent lease. Service Bus sessions may improve per-key sequencing but cannot correct out-of-order arrival at the provider boundary.

| Provider | Authoritative state-change time | State mapping |
|---|---|---|
| Quetzal | `data.charge.paid_at` for capture; `data.charge.refunded_at` for refund; otherwise `created` when no more specific transition timestamp exists | `charge.succeeded` → `Captured`; `charge.failed` → `Failed`; `charge.refunded` → `Refunded` |
| Andina | `transaccion.fecha_transaccion` | `PENDIENTE` → `Pending`; `APROBADA` → `Captured`; `RECHAZADA` → `Failed`; `REVERSADA` → `Refunded` |
| Austral | `payment.occurredAt` | `PAYMENT_CONFIRMED` → `Captured`; `PAYMENT_REJECTED` → `Failed`; `PAYMENT_REVERSED` → `Refunded` |

All timestamps are normalized to UTC. For a given payment, an event with an earlier authoritative state-change time than the last accepted state is recorded as `SKIPPED_STALE` and must not update Salesforce. A later valid reversal/refund supersedes an earlier capture. `Pending` must not overwrite a terminal state. `Failed` must not overwrite a later capture or refund. Equal timestamps with different states, invalid lifecycle transitions, or conflicting provider evidence are marked `RECONCILIATION_REQUIRED` rather than resolved by arrival order or a blanket numerical status rank.

The Worker compares and reserves the next valid state under the per-payment lock, then performs the Salesforce upsert through the approved enterprise integration path. A durable per-payment `IN_FLIGHT` reservation (including an expiring lease, operation ID, target state and event ID) prevents another Worker replica from issuing a newer write until the earlier operation is confirmed or reconciled. An expired lease is not evidence that Salesforce did not apply the request. A retry or reconciliation is required when the remote result is uncertain. The Worker must not release an earlier unresolved operation in a way that permits a later operation to be overwritten by a delayed retry. Before replaying any timed-out operation, it compares the remote `Status_Changed_At__c` and `Last_Event_Id__c` with the intended transition. This guards against late retries initiated by this Worker, but a strict remote compare-and-set guarantee would require Salesforce-side conditional enforcement not specified in the assessment. Salesforce `Status_Changed_At__c` and `Last_Event_Id__c` provide audit evidence, but the Worker remains responsible for state-transition checks; Salesforce's external-ID upsert alone does not implement ordering.

### 2.5 Retry, Dead-Letter and Replay Policy

The following values are **initial design settings**, not provider-mandated values. They must be tuned through load and incident testing.

| Failure category | Policy | Terminal or recovery action |
|---|---|---|
| Invalid webhook signature, timestamp or schema | Reject immediately; do not publish | Provider-specific non-2xx response, security metric, no business event |
| Intake PostgreSQL unavailable | No successful acknowledgement before commit | Return retryable non-2xx within provider timeout; rely on documented provider retries |
| Intake Outbox publication failure | Retry after 5 s, 15 s, 45 s, then exponential backoff capped at 5 min with jitter | Retain durable Outbox row and alert on age; never drop accepted event |
| Salesforce `500`, `502`, `503`, network timeout | Durable retry after 15 s, 30 s, 1 min, 2 min, 5 min, then up to 15 min with jitter | Reconcile uncertain writes before re-upsert; escalate if recovery exceeds 1 h |
| Salesforce `400 UNABLE_TO_LOCK_ROW` | Retry with short exponential backoff and jitter | Serialize per payment; escalate repeated contention |
| Salesforce `401 INVALID_SESSION_ID` | Refresh cached OAuth token and retry once | Escalate persistent authentication failures |
| Salesforce `403 REQUEST_LIMIT_EXCEEDED` or exhausted local quota | Pause Salesforce writes; resume after capacity is available | Keep durable deferred work; alert on quota pressure and queue age |
| Salesforce `400` field/validation error | Do not retry unchanged payload | Record `FAILED_PERMANENT`; send to DLQ/remediation after durable failure record |
| Repeated poison-message or mapping failure | Bounded delivery attempts; default maximum 10 | DLQ with correlation, payment key, sanitized error code and replay metadata |

A deferred message is **not completed** merely because a retry is planned. The Worker first persists a recoverable work item, its next-attempt time, and the outcome/Outbox state in its own database; only then may it complete the original Service Bus delivery. A scheduled dispatcher re-enqueues due work using stable event identifiers. Database transactions and broker acknowledgements are not atomic, so duplicate re-enqueueing is expected and handled by idempotency. Service Bus lock renewal is bounded; long Salesforce outages must not rely on holding an active message lock.

The Worker database retains `retry_count`, `next_attempt_at`, `failure_category`, `last_error_code`, `reconciliation_required`, and `processing_status`. A dedicated replay command accepts an event ID or DLQ message ID, verifies authorization, preserves the original canonical event and payment keys, checks the latest provider/Salesforce state when needed, and republishes through the same processing path. Replay is audited and cannot bypass ordering or idempotency. Operational alerts fire on DLQ count greater than zero, oldest pending event age over 5 minutes, and any unacknowledged durable intake event older than 1 minute. These are initial alert thresholds to be validated.

Provider-specific webhook acknowledgement constraints are: Quetzal any `2xx` within 10 seconds, Andina **only `200` or `204`** within 5 seconds, and Austral any `2xx` within 10 seconds. The Intake service should target a response well below the strictest 5-second deadline. The prior generic `202 Accepted` example is **not valid for Andina**.

### 2.6 Capacity, Salesforce API Budget and Freshness

The source workload is approximately 15,000 notifications/day normally and 120,000/day during month-end, including about 3% duplicates. The peak burst is 40 notifications/second for 15 minutes (36,000 deliveries). Assuming duplicates are evenly distributed, the month-end unique-event volume is approximately `120,000 × 0.97 = 116,400` events/day, or 1.35 unique events/second averaged across the day. The 40/second burst yields approximately 38.8 unique events/second. The Intake service and Outbox must handle the **full** incoming 40/second rate, since authentication and deduplication occur before suppression.

Salesforce allows up to 200 records in one collection upsert request, counted as one API call. The design uses a **target batch size of 100 records**, with a maximum wait of **2 seconds** for a ready batch; it flushes smaller batches to protect latency. This is a starting configuration, not a promise of 100-record occupancy during normal traffic. At 100 records per call, the theoretical month-end volume requires about `ceil(116,400 / 100) = 1,164` successful batch calls/day, excluding retries, reads, and reconciliation. In the worst case of one upsert per unique event, the same workload needs approximately 116,400 write calls/day, leaving only 33,600 calls for retries and other integration operations within the 150,000-call allocation. Batching is therefore essential for resilience and headroom, not merely cost optimization.

During a 40/second burst, 100-record batches would require roughly 0.4 Salesforce requests/second **if batches fill**. With the proposed 2-second flush interval, a sustained 40/second stream yields at most approximately 80 records per batch before deduplication; therefore 0.4 requests/second is a best-case batching illustration, not the expected measured rate. Using the documented p95 batch latency of 4 seconds, the average in-flight concurrency at that rate is approximately 1.6 requests; this is well below the maximum 25, but actual concurrency must be bounded across all Worker replicas. A conservative initial **global** Salesforce request cap of 10 concurrent calls and a rolling 24-hour request counter are proposed, with a reserve of at least 20% of the allocated budget for retries, reconciliation and operational traffic. These values are configuration choices and must be tested under partial failures.

The Salesforce team reports approximately 97% monthly availability and outages lasting 5–90 minutes. Therefore, the requirements that 95% of payments be visible within 5 minutes and 99.5% within 1 hour are **targets to measure**, not guarantees that can be met during every outage: a 90-minute complete outage makes the one-hour target impossible for notifications received near its beginning. The architecture minimizes controllable latency and supports catch-up, while reporting unavoidable upstream dependency breaches explicitly rather than masking them.

For illustration, a 90-minute outage at the **average month-end rate** accumulates approximately `120,000 / 86,400 × 5,400 ≈ 7,500` incoming notifications. At a sustained 40/second rate, 15 minutes contributes 36,000 deliveries. The queue and Outbox retention must safely hold these backlogs plus retry overhead. With 100-record batches, 36,000 unique records represent roughly 360 collection upsert requests, ignoring duplicates and retries. At a conservative 2 requests/second after recovery, that workload could be submitted in about 3 minutes, **excluding** record-level failures, upstream gateway overhead, and competing traffic. This is an illustrative throughput calculation, not a measured recovery guarantee.

Per-country dashboards track the elapsed time from the provider's event notification to confirmed Salesforce recording, with p95 and p99.5 freshness, accepted-but-unpublished Outbox age, Service Bus backlog age, success/failure/reconciliation counts, API call consumption, and DLQ depth. Load tests must reproduce the 40/second burst, normal month-end volume, Salesforce 5–90-minute outages, `REQUEST_LIMIT_EXCEEDED`, duplicate redelivery, and out-of-order refunds. Results and measured SLO compliance belong in the operational documentation.


## 3. Canonical Payment Event and Provider Mapping

### 3.1 Canonical Event Contract

The solution defines a versioned canonical payment event that decouples provider-specific webhook payloads from Salesforce, Finance, and Notifications consumers.

Each accepted business event is transformed into a consistent representation before being persisted in the Intake Outbox and published to Azure Service Bus.

The canonical event contains the following fields:

| Field | Type | Required | Description |
|---|---|---|---|
| `eventId` | UUID string | Yes | Unique identifier generated for the canonical event. |
| `schemaVersion` | string | Yes | Contract version, initially `1.0`. |
| `eventType` | string | Yes | Canonical event type: `premium.payment.updated`. |
| `provider` | string | Yes | `Quetzal`, `Andina`, or `Austral`. |
| `country` | string | Yes | ISO country code: `MX`, `CO`, or `CL`. |
| `providerEventId` | string | Yes | Original provider notification or event identifier. |
| `providerPaymentId` | string | Yes | Original charge, transaction, or payment identifier. |
| `paymentKey` | string | Yes | Stable, country-prefixed payment identifier used for Salesforce upserts. |
| `idempotencyKey` | string | Yes | Provider-specific business event deduplication key. |
| `policyReference` | string | Yes | Meridian policy number associated with the payment. |
| `status` | string | Yes | `Pending`, `Captured`, `Failed`, or `Refunded`. |
| `originalStatus` | string | Yes | Original provider event type or payment state. |
| `amount.value` | decimal string | Yes | Payment amount expressed in major currency units. |
| `amount.currency` | string | Yes | ISO 4217 currency code. |
| `occurredAt` | ISO 8601 string | Yes | Provider's authoritative payment state-change timestamp, normalized to UTC. |
| `receivedAt` | ISO 8601 string | Yes | Time the notification was received by Meridian, normalized to UTC. |
| `correlationId` | string | Yes | Identifier used for distributed tracing and operational investigation. |
| `metadata.refundedAmount` | decimal string | No | Amount refunded, when supplied by the provider. |
| `metadata.reasonCode` | string | No | Provider failure or reversal reason, when available. |

The canonical contract excludes payer names, national identification numbers, email addresses, payment tokens, and other unnecessary personal or sensitive information.

Amounts are represented as decimal strings to preserve precision and prevent floating-point rounding errors.

All monetary values are normalized to major currency units. Currency codes are uppercase ISO 4217 values, and timestamps are normalized to UTC.

### 3.2 Provider Field Mapping

The following mappings are derived from the provider webhook specifications and supplied sample payloads.

| Canonical field | Quetzal — Mexico | Andina — Colombia | Austral — Chile |
|---|---|---|---|
| `provider` | `Quetzal` | `Andina` | `Austral` |
| `country` | `MX` | `CO` | `CL` |
| `providerEventId` | `id` | `notificacion_id` | `eventId` |
| `providerPaymentId` | `data.charge.id` | `transaccion.id` | `payment.paymentId` |
| `paymentKey` | `MX:{data.charge.id}` | `CO:{transaccion.id}` | `CL:{payment.paymentId}` |
| `idempotencyKey` | `Quetzal:{id}` | `Andina:{transaccion.id}:{transaccion.estado}` | `Austral:{eventId}` |
| `policyReference` | `data.charge.reference` | `transaccion.referencia` | `payment.policyNumber` |
| `originalStatus` | `type` | `transaccion.estado` | `eventType` |
| `amount.value` | `data.charge.amount / 100` | `transaccion.valor` | `payment.amount` |
| `amount.currency` | Uppercase `data.charge.currency` | `transaccion.moneda` | `payment.currency` |
| `occurredAt` | `paid_at`, `refunded_at`, or `created`, according to the event type | `transaccion.fecha_transaccion` | `payment.occurredAt` |
| `metadata.refundedAmount` | `data.charge.amount_refunded / 100` | Not supplied | Not supplied |
| `metadata.reasonCode` | `data.charge.failure_code` | `transaccion.motivo_reverso` | Not supplied |

The `receivedAt`, `eventId`, and `correlationId` fields are generated or assigned by Meridian's integration components.

For Quetzal, `created` is expressed in Unix seconds. The more specific `paid_at` and `refunded_at` timestamps take precedence for their corresponding transitions.

For Andina, `fecha_notificacion` represents a delivery attempt and must not be used to determine the payment's business ordering. `fecha_transaccion` is authoritative.

For Austral, `sentAt` represents event delivery timing, while `payment.occurredAt` identifies the business state-change time.

### 3.3 Provider State Normalization

| Provider | Original state or event | Canonical status |
|---|---|---|
| Quetzal | `charge.succeeded` | `Captured` |
| Quetzal | `charge.failed` | `Failed` |
| Quetzal | `charge.refunded` | `Refunded` |
| Andina | `PENDIENTE` | `Pending` |
| Andina | `APROBADA` | `Captured` |
| Andina | `RECHAZADA` | `Failed` |
| Andina | `REVERSADA` | `Refunded` |
| Austral | `PAYMENT_CONFIRMED` | `Captured` |
| Austral | `PAYMENT_REJECTED` | `Failed` |
| Austral | `PAYMENT_REVERSED` | `Refunded` |

The normalization layer preserves the original provider state while exposing a consistent business status to downstream consumers.

Unknown states are not automatically mapped to an existing canonical status. They are classified as unsupported and routed for investigation.

A `Refunded` event must not be overwritten by an earlier `Captured` event. Likewise, a late `Pending` event must not replace an already confirmed or reversed payment state.

These ordering guarantees are enforced by the Salesforce Worker using the strategy defined in Section 2.4.

### 3.4 Monetary Normalization

The provider adapters must apply currency-specific normalization before publishing canonical events.

**Quetzal — MXN**

Quetzal provides amounts in minor currency units. The adapter divides the integer amount by 100 using exact decimal arithmetic.

For example:

`125000` → `"1250.00"` MXN.

The same conversion applies to `amount_refunded`. A partial refund must be identified by comparing the normalized refunded amount with the original charge amount. Since Salesforce exposes only `Refunded` and no `PartiallyRefunded` status, partial refunds require a business decision from the Salesforce/policy owners before the Worker changes `Status__c`; such events remain durably recorded and are marked `RECONCILIATION_REQUIRED` pending that decision. They must not automatically trigger the full-refund policy automation.

**Andina — COP**

Andina provides amounts as decimal strings in major currency units.

For example:

`"185000.00"` → `"185000.00"` COP.

No division is required.

**Austral — CLP**

Austral provides amounts as integer strings in major currency units because CLP has no minor unit.

For example:

`"45990"` → `"45990"` CLP.

No division is required.

The integration must not convert monetary amounts using binary floating-point arithmetic.

### 3.5 Salesforce Field Mapping

The Salesforce Worker maps the canonical event into the `Premium_Payment__c` object.

| Salesforce field | Canonical source |
|---|---|
| `Payment_Key__c` | `paymentKey` |
| `Policy__r.Policy_Number__c` | `policyReference` |
| `Amount__c` | `amount.value` |
| `CurrencyIsoCode` | `amount.currency` |
| `Status__c` | `status` |
| `Provider__c` | `provider` |
| `Provider_Transaction_Id__c` | `providerPaymentId` |
| `Status_Changed_At__c` | `occurredAt` |
| `Last_Event_Id__c` | `providerEventId` |

The Worker uses `Payment_Key__c` as the unique external identifier for idempotent upserts.

The policy relationship is resolved through `Policy__r.Policy_Number__c`, without requiring the integration to manage Salesforce internal record identifiers.

The integration must never directly update `Policy__c.Status__c`.

Salesforce automation remains responsible for policy reinstatement or suspension following a payment capture or refund.

If the referenced policy does not exist, the Salesforce request fails and the event is routed to the documented remediation process.

### 3.6 Contract Versioning and Compatibility

The canonical event initially uses schema version `1.0`.

Backward-compatible changes may introduce optional fields without changing the meaning of existing fields.

Breaking changes, including renamed mandatory fields, incompatible data types, or modified business semantics, require a new major contract version.

Consumers must validate the schema version and reject or quarantine unsupported major versions.

The JSON Schema is maintained in:

`docs/api/events/premium-payment.schema.json`

The schema is the authoritative machine-readable contract used by automated validation and contract tests.

Provider-specific adapters may evolve independently without requiring downstream changes, provided that the canonical contract remains compatible.


## 4. Persistence Model, Indexing and Retention

### 4.1 Persistence Boundaries

PostgreSQL is selected for transactional outbox consistency, uniqueness enforcement, operational queries, and per-payment state coordination. The two services use separate logical databases and credentials; neither service reads the other service's database. The schemas below are **proposed implementation models**, not existing Salesforce or provider schemas. All timestamps are stored as `TIMESTAMPTZ` in UTC. UUIDs are generated by Meridian.

### 4.2 Intake Database

| Table | Key fields and purpose | Constraints and indexes |
|---|---|---|
| `webhook_notification` | `id UUID PK`, `provider`, `country`, `provider_event_id`, `provider_payment_id`, `notification_business_key`, `payment_key`, `canonical_event_id UUID`, `payload_hash`, `received_at`, `occurred_at`, `acceptance_status`, `correlation_id` | `UNIQUE(provider, notification_business_key)`; indexes on `(provider, provider_event_id)`, `(provider, provider_payment_id)`, `(payment_key, received_at DESC)`, `(received_at)` |
| `intake_outbox` | `id UUID PK`, `aggregate_key`, `event_id UUID`, `event_type`, `schema_version`, `payload JSONB`, `state`, `attempt_count`, `next_attempt_at`, `published_at`, `created_at` | `UNIQUE(event_id)`; partial index on `(next_attempt_at, created_at)` where `state = 'PENDING'`; index on `aggregate_key` |
| `notification_status` | `canonical_event_id UUID PK`, `notification_id UUID FK`, `provider`, `provider_event_id`, `provider_payment_id`, `payment_key`, `status`, `salesforce_status`, `last_error_code`, `last_updated_at`, `correlation_id`, `result_version` | indexes on `(provider, provider_event_id)`, `(provider, provider_payment_id)`, `(status, last_updated_at)`, `(payment_key)` |
| `processed_result_event` | `result_event_id UUID PK`, `canonical_event_id`, `processed_at` | Unique `result_event_id` to suppress duplicate result messages |

A single local transaction inserts `webhook_notification` and `intake_outbox`; the initial operational projection is created in the same transaction. Repeated deliveries do not create new canonical events. If a provider changes the delivery identifier between attempts, that identifier is recorded in a separate `webhook_delivery_attempt` table (`id`, `notification_id`, `provider_delivery_id`, `received_at`, `payload_hash`, `result`) so Operations can search any attempt without weakening business-event deduplication. Store only an allowlisted, redacted representation or a digest of the original payload; do not persist unnecessary payer PII.

Outbox publishers claim rows using `FOR UPDATE SKIP LOCKED` in short transactions and use a claim lease or state transition to avoid concurrent publication. Publication and database marking are not atomic; consumers remain idempotent.

### 4.3 Salesforce Worker Database

| Table | Key fields and purpose | Constraints and indexes |
|---|---|---|
| `payment_processing_state` | `payment_key TEXT PK`, `provider`, `current_status`, `current_occurred_at`, `last_canonical_event_id`, `last_provider_event_id`, `salesforce_status`, `operation_id`, `lease_expires_at`, `updated_at` | Primary-key locking for per-payment serialization; index on `(salesforce_status, updated_at)` |
| `processed_payment_event` | `consumer_name`, `canonical_event_id`, `payment_key`, `event_status`, `occurred_at`, `processing_status`, `retry_count`, `next_attempt_at`, `failure_category`, `last_error_code`, `reconciliation_required`, `correlation_id` | `PRIMARY KEY(consumer_name, canonical_event_id)`; index on `(processing_status, next_attempt_at)`; index on `(payment_key, occurred_at DESC)` |
| `worker_outbox` | `id UUID PK`, `event_id UUID`, `canonical_event_id`, `payload JSONB`, `state`, `attempt_count`, `next_attempt_at`, `published_at`, `created_at` | `UNIQUE(event_id)`; partial index on `(next_attempt_at)` where `state = 'PENDING'` |
| `replay_audit` | `id UUID PK`, `canonical_event_id`, `requested_by`, `reason`, `requested_at`, `outcome`, `correlation_id` | index on `(canonical_event_id, requested_at DESC)` |

The Worker first persists its claim/reservation, then performs an external integration call, then commits the outcome and corresponding result Outbox event together. A crash or timeout between the remote write and the local outcome requires reconciliation by Salesforce external payment key. For composite upserts with `allOrNone: false`, each record result is recorded independently; one failed record must not cause successful records to be resent blindly.

### 4.4 Retention, Recovery and Cost

**Proposed retention settings, subject to Meridian's legal and operational approval:** retain notification metadata and operational statuses for 90 days; retain processed-event deduplication records and replay audit for at least 90 days; retain published Outbox entries for 7 days after confirmed publication; retain dead-letter and unresolved reconciliation records until remediation, with a 90-day review and no automatic deletion of unresolved work. Preserve deduplication tombstones for at least the longest provider retry window (Quetzal: 3 days) and for the full operational replay window. Do not expire deduplication state while its corresponding event remains replayable.

Partition `webhook_notification`, `webhook_delivery_attempt`, and high-volume historical event tables monthly by `received_at` only when measurement justifies the operational complexity; start with indexed PostgreSQL tables. Use managed PostgreSQL backups and point-in-time recovery, Service Bus dead-lettering, and Azure Monitor alerts. Azure Container Apps consumption-based scaling and Service Bus managed messaging avoid always-on VM or Kubernetes administration; two small relational databases and retained events are the main recurring stateful costs. Cost assumptions and actual sizing must be validated against the selected Azure region and pricing tier.

## 5. Operational Lookup, Security and Observability

### 5.1 Operations API

Operations accesses the Intake service **only through the existing API Manager**, using Meridian's OIDC/OAuth 2.0 identity provider and a dedicated least-privilege role such as `payments.operations.read`. No direct access to PostgreSQL, the Salesforce Worker, or raw logs is required.

| Endpoint (proposed) | Purpose |
|---|---|
| `GET /api/v1/notifications?provider={provider}&providerEventId={id}` | Find by original notification/event identifier, including attempt IDs where applicable |
| `GET /api/v1/notifications?provider={provider}&providerPaymentId={id}` | Find all notifications associated with a provider payment/transaction |
| `GET /api/v1/notifications/{notificationId}` | Return one notification's lifecycle, timestamps, canonical event ID, correlation ID, and sanitized error information |
| `GET /api/v1/payments/{paymentKey}/events` | List the payment's accepted, skipped, recorded, failed, and replayed transitions |
| `POST /api/v1/operations/replays` | Request an audited replay; separate privileged `payments.operations.replay` role, with reason and event identifier |

Queries are paginated and bounded. API Manager validates issuer, audience, expiry, and role claims; access is audited, rate-limited, and limited to necessary country scope when applicable. No personal identifiers, payment tokens, secrets, or raw payloads are returned.

### 5.2 Lifecycle and Consistency

The operational read model reports `RECEIVED`, `PUBLISHED`, `PROCESSING`, `RECORDED`, `RETRY_SCHEDULED`, `RECONCILIATION_REQUIRED`, `FAILED_PERMANENT`, `SKIPPED_STALE`, and `DEAD_LETTERED`. These are **internal processing statuses**, distinct from the four canonical payment business statuses. Results carry a monotonically increasing per-event processing version; the Intake projection applies only newer versions, so delayed result messages cannot regress the displayed status. The projection is eventually consistent with the Worker and must expose `lastUpdatedAt` and a freshness indicator.

The end-to-end `correlationId` is propagated through API Manager, Intake, Outbox, Service Bus application properties, Worker, integration gateway, and result events. Telemetry includes provider and country, accepted/duplicate/rejected counts, queue and Outbox age, end-to-end Salesforce visibility latency, API usage and throttling, retry attempts, reconciliation backlog, and dead-letter depth. Logs and traces contain only approved identifiers and sanitized errors.

### 5.3 Freshness Measurement and Alerts

Measure freshness from Meridian's receipt of the provider notification until Salesforce confirms the payment write, using `receivedAt` and `recordedAt` in UTC. Report 5-minute and 1-hour compliance by country and globally, distinguishing dependency outages from controllable pipeline delay without excluding them from the observed metric. Alerts include pending Outbox age over 1 minute, oldest unrecorded accepted notification over 5 minutes, any DLQ message, unexpected authentication failures, approaching the shared Salesforce API budget, and sustained Service Bus backlog growth. Alert thresholds are initial design values subject to operational tuning.
