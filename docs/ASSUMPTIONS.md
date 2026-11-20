# Assumptions

The following are **design assumptions or unresolved external business dependencies**, not statements guaranteed by the provided specifications:

| # | Assumption | Why I needed it | Part(s) affected |
|---|---|---|---|
| 1 | Partial refunds | Quetzal can send partial refunds, but the supplied Salesforce picklist has no `PartiallyRefunded` value | Preserve `amount_refunded` and route a partial refund for reconciliation until Salesforce and business owners approve the required status/automation behavior. Do not misclassify a partial refund as a full refund |
| 2 | Andina event identity | `(transaccion.id, transaccion.estado)` uniquely identifies a business transition for the assessment examples | Repeated genuine transitions to the same state cannot be distinguished from retries using the documented fields; quarantine conflicting evidence and obtain provider clarification |
| 3 | Enterprise gateway | Salesforce REST requests are routed through an approved API Manager/integration application path, consistent with the scenario's no-point-to-point rule | Whether Mule 4 mediation is required for outbound calls is an enterprise configuration decision; the design does not introduce a third owned microservice |
| 4 | Remote concurrency | Local Worker serialization and reconciliation prevent competing requests from this integration | Absolute prevention of stale writes from unrelated Salesforce clients would require Salesforce-side conditional validation or ownership controls beyond the supplied model |
| 5 |Freshness during outages | The 95%/5-minute and 99.5%/1-hour targets cannot be guaranteed during all documented 90-minute Salesforce outages | Measure actual compliance, preserve accepted events, and prioritize catch-up after recovery |
| 6 | Retention and deployment | The retention periods, database sizing, worker concurrency cap, and retry delays are initial design choices to validate with privacy, platform, Salesforce and operations stakeholders |  |

### 6.1 Verification Plan

- Verify the supplied provider HMAC test vectors against the exact raw sample bytes, including Quetzal dual signatures, Andina Base64 encoding, and Austral raw-body signing.
- Verify Andina repeated deliveries with changed `notificacion_id` generate one business event, while a later `REVERSADA` generates a distinct transition.
- Verify an older `Captured` event cannot overwrite a newer `Refunded` state, including after a Worker crash and replay.
- Verify Salesforce composite results handle partial record success and `INVALID_FIELD`, `UNABLE_TO_LOCK_ROW`, `INVALID_SESSION_ID`, and `REQUEST_LIMIT_EXCEEDED` correctly.
- Load-test 40 requests/second for 15 minutes, 120,000 notifications/day, 3% duplicates, queue recovery after 5–90-minute Salesforce outages, and all three provider acknowledgement deadlines.
- Verify authorized operational lookup by provider event ID, provider payment ID, and Andina retry attempt ID; deny unauthenticated or unauthorized access.
- Validate that no successful webhook acknowledgement precedes durable database commit, and that replay cannot bypass idempotency or ordering.
