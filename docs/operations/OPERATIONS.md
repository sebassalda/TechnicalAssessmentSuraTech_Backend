# Operations — Premium Payment Notifications

## Scope and ownership
Payments Integration owns webhook acceptance, durable event delivery, Salesforce payment upsert orchestration and incident response. Salesforce automation—not this integration—reinstates policies. Primary on-call: Payments Integration; escalation: Salesforce team, integration platform/API Manager team and Azure platform team.

## SLIs / SLOs (per country: MX, CO, CL)
| SLI | Measurement | SLO / alert |
|---|---|---|
| Payment freshness | Time from provider notification received to `Premium_Payment__c` visible in Salesforce, segmented by country, measured on a rolling 24h window | >=95% within 5 min; >=99.5% within 1 hour (NFR2) |
| Accepted-event durability | Accepted 2xx notifications that remain durably traceable until completed, quarantined or explicitly reconciled; count confirmed lost events | Zero lost accepted events (NFR3) |
| Queue / DLQ health | Oldest message age, active depth, DLQ count and growth by country | Page on any sustained DLQ growth or oldest active age >5 min; investigate immediately if DLQ >0 |
| Salesforce error rate | Upsert 4xx (permanent vs transient), 5xx, 429 and API budget usage | Page if 4xx >1% for 5 min or any sustained surge; warn at 70% and page at 85% of rolling 24h integration budget |
| Webhook acknowledgement | Valid requests acknowledged within provider-specific timeout; invalid signatures rejected | Quetzal <10s; Andina <5s; alert on p95 >50% of timeout |

SLO windows, exact paging thresholds and paging policy are proposed operating defaults and require production baselining. Measure freshness against the **original provider receipt time**, not only the consumer processing time. Exclude rejected unauthenticated requests from accepted-event durability denominator, but report them separately.

## Instrumentation and tracing
- Generate/propagate a `correlationId` at ingress through API Manager, verified webhook, canonical event, inbox/outbox, Service Bus message properties, consumer, Salesforce request and operational status record. Preserve provider IDs, idempotency key, country and event version as structured fields.
- Record lifecycle timestamps (`receivedAt`, `persistedAt`, `publishedAt`, `salesforceRecordedAt`), state, attempt count, queue, DLQ reason and sanitized Salesforce error code. Expose an authenticated operational lookup by provider and transaction identifiers, with least-privilege access and audit trail (FR7).
- Emit metrics for intake count, HMAC failures, deduplication, persistence errors, outbox age, publish retries, queue depth, DLQ depth, consumer retries, Salesforce 4xx/429/5xx, API budget and freshness histograms per country. Trace asynchronous links using W3C trace context where possible.
- **Never log** HMAC secrets, authorization headers, payment tokens, raw webhook bodies, payer names, national IDs, emails or other personal data. Mask policy references when they may identify a person; restrict trace access and retention.

## Response playbook
1. Acknowledge alert; declare severity based on customer impact and SLO risk; identify country and first affected timestamp.
2. Verify intake, queue and DLQ, outbox, consumer errors and Salesforce responses; distinguish permanent 4xx from 429/5xx.
3. Stop rapid retries of permanent failures; quarantine and preserve evidence. Do not discard or blindly replay messages.
4. Coordinate mapping/reference-data fix with Salesforce owner. Reconcile impacted payments and ordering; replay controlled batches with stable idempotency keys and a rate limit respecting the shared Salesforce API budget.
5. Verify Salesforce payment records, downstream event delivery and freshness recovery. Communicate customer impact and close only after reconciliation.
