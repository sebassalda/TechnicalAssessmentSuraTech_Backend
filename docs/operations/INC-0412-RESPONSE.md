# INC-0412 — Incident response (SEV-2 proposed)

**Incident:** Monday 2026-11-30 08:12 COT. 37 Colombian customers report approved Andina payments while policies remain suspended. This scenario describes an existing **v1**, not necessarily the architecture proposed in Part A.

## First 30 minutes (08:12–08:42 COT)
| Window | Action and rationale |
|---|---|
| 0–5 min | Acknowledge SEV-2, open incident channel, assign incident commander/comms lead, inform Colombia contact center and Salesforce on-call. Confirm affected customers and no direct policy-status edits. |
| 5–10 min | Check `payments-co` DLQ and consumer traces against MX/CL; confirm 4,312 Colombian DLQ messages, 43,120 Salesforce 400 responses, near-empty active queue, zero signature failures. Identify the first dead-lettered message at 2026-11-28 23:41 COT; investigate earlier processing failures in consumer logs and Salesforce error telemetry. |
| 10–15 min | Sample sanitized DLQ errors and compare `referencia` with Salesforce `Policy__c.Policy_Number__c` external ID. Check if leading zeros, prefixes, country-specific normalization or missing policy records explain failures. Request Salesforce external-ID query and schema/mapping verification. |
| 15–20 min | Prevent immediate retries for non-retryable 400 `INVALID_FIELD` errors; preserve DLQ and correlation IDs. Check integration budget (66% rolling 24h versus 38% baseline), suspend uncontrolled replay and establish safe rate limits. |
| 20–25 min | Compare first failure with Friday v1.14.0 deployment and Saturday Salesforce change; inspect release diffs and reference-data availability. Validate a corrected lookup/mapping on masked samples in a non-production setting. |
| 25–30 min | Communicate impact, mitigation and next update. Prepare reconciliation list by unique `(provider, transaction ID, status)`; plan approved staged replay only after fix and Salesforce coordination. |

## Most likely cause and verification
**Leading hypothesis:** Colombia-specific policy reference resolution is failing at the Salesforce upsert boundary. Evidence: `400 INVALID_FIELD: Foreign key external ID ... not found for field Policy_Number__c`, 43,120 Colombian 400s, 4,312 DLQ messages all marked `MaxDeliveryCountExceeded`, and no similar surge in MX/CL. Samples show inconsistent formats (`000215560` versus `pol-co-000198004`), suggesting a mapping or reference-data mismatch. This is **not yet proof** of which system introduced the mismatch.

Confirm by comparing canonical reference, transformed Salesforce external ID and the actual Salesforce `Policy__c` record for a sample of affected and successful transactions. Check country-specific mapping versions, policy sync completeness and external-ID uniqueness. Correlate first failures with the 2026-11-27 18:05 integration deployment and 2026-11-28 22:00 Salesforce release; neither change should be blamed without evidence. The Salesforce validation rule concerns cancellation reasons and is not directly supported as the cause by the observed foreign-key error.

## Remediation and safe replay
1. Correct verified mapping or missing reference data with Salesforce team; validate on representative samples including approved, reversed and rejected events. Route permanent 400s to a triage/quarantine workflow, **not** immediate abandon/retry loops.
2. Export/snapshot DLQ metadata and counts for audit; retain original messages until confirmed processed. Inventory by transaction and status, and reconcile against current Salesforce payment records and provider's latest state. For `TRX-CO-5603318`, both `APROBADA` and `REVERSADA` exist: never replay approval in a way that overwrites the later reversal.
3. Replay in controlled batches via a reviewed tool with dry-run preview, idempotent Salesforce external IDs, per-transaction ordering/state reconciliation, bounded retry/backoff and API-budget guardrails. Treat 400 mapping errors as terminal pending manual repair; retry 429/5xx with backoff and jitter. Record replay actor, timestamp, outcome and correlation IDs.
4. Validate the resulting `Premium_Payment__c` records and policy linkage. Let Salesforce automation handle policy reinstatement; escalate missing reinstatements separately. Reconcile the 37 reported cases and all impacted transactions, including reversals. Monitor DLQ, Salesforce 400s, freshness and budget until stable.

## Prevention
- Add DLQ-depth and DLQ-growth alerts for **all** country queues (only MX had one), plus Salesforce 400 error-rate and age-of-oldest-event alerts; ensure paging and synthetic checks.
- Contract tests for Colombian policy reference formats, leading zeros, prefix variants, missing-policy cases and reversal ordering; pre-deploy cross-team compatibility checks.
- Classify permanent 4xx vs retryable 429/5xx; bounded backoff and quarantine prevent 10 rapid retries and API budget waste.
- Add secure notification lookup and country-level freshness dashboards; rehearse replay with idempotency and order preservation.
