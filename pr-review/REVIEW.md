# Review — PR-214

## Verdict

- [ ] Approve
- [ ] Approve with comments
- [x] **Request changes**

## Summary for the author

Hi @dvaldes, thanks for adding the Austral webhook and keeping the handler responsive to the provider's 10-second deadline. The separation of signature verification, controller, and payment service is a useful starting point. I cannot approve this version because an unsigned request can be accepted, the 2xx acknowledgment precedes durable intake, and downstream failures are swallowed. Those issues can cause unauthorized payment updates or permanently lost notifications. Please address the blockers and major findings below, and add tests using Austral's published sample payloads and signatures. Happy to review the revision.

## Findings

| # | Severity | File:line | Finding | Why it matters in this scenario | Suggested fix |
|---|---|---|---|---|---|
| 1 | **Blocker** | `src/main/java/com/meridian/payments/cl/AustralWebhookController.java:43` | Missing signature bypasses verification (`signature != null && ...`). | Anyone able to reach the endpoint could submit fabricated payment state changes. | Reject missing/blank signatures with 401; verify HMAC against the **raw request bytes** before parsing or queuing. Add missing/invalid/valid signature tests. |
| 2 | **Blocker** | `src/main/java/com/meridian/payments/cl/AustralWebhookController.java:52-54` | Returns 200 after enqueueing work only in an in-process executor. | A crash/restart after acknowledgment can permanently lose an accepted payment notification; Austral will not retry a 2xx. | Verify and persist an inbox/outbox record in a committed transaction **before** returning 2xx; process asynchronously from durable storage. Return a retryable non-2xx when durable intake fails. |
| 3 | **Blocker** | `src/main/java/com/meridian/payments/cl/AustralPaymentService.java:59-61` | Catches and logs all exceptions without propagating or recording failure. | Salesforce failures disappear from the retry path; the provider already received 200. | Persist retryable failure state and retry with backoff/DLQ; do not mark the event complete until the downstream effect is confirmed. |
| 4 | **Blocker** | `src/main/java/com/meridian/payments/cl/AustralPaymentService.java:42-49` | Unconditionally updates an existing payment's status without comparing event times. | Austral does not guarantee ordering; an old `PAYMENT_CONFIRMED` can overwrite a later `PAYMENT_REVERSED`, leading to incorrect customer outcomes. | Store and compare `payment.occurredAt` and enforce valid transitions/monotonic versioning; test reversed-then-confirmed delivery. |
| 5 | **Major** | `src/main/java/com/meridian/payments/cl/AustralNotification.java:12` | `amount` is `double`, but the provider contract defines a **string integer** representing CLP units. | Numeric coercion and floating-point conversion can silently alter financial amounts or reject otherwise valid payloads. | Parse the string as `BigDecimal` or integer exact CLP units, validate scale 0 and currency `CLP`; remove floating-point rounding in `AustralPaymentService.java:52`. |
| 6 | **Major** | `src/main/java/com/meridian/payments/cl/AustralPaymentService.java:36-37,59` | `existsById` then `save` is not an atomic idempotency claim; the processed marker is written after the Salesforce call. | Concurrent retries can both call Salesforce; an external write may succeed before the local transaction fails. | Use an atomic unique-key inbox insert and a durable processing state; design Salesforce upserts around a stable external ID and retry-safe operations. |
| 7 | **Major** | `src/main/java/com/meridian/payments/cl/AustralPaymentService.java:43-56` | Uses find-then-create/update against Salesforce, a non-atomic multi-call sequence. | Concurrent notifications can create duplicate payment records and increase Salesforce API consumption. | Use Salesforce external-ID upsert and state-transition guards; batch where appropriate and respect API budget. |
| 8 | **Major** | `src/main/java/com/meridian/payments/cl/AustralPaymentService.java:65-70` | Unknown `eventType` silently maps to `Pending`. | A newly introduced or malformed provider event can incorrectly change payment status. | Reject or quarantine unsupported event types with a documented classification; test the behavior. |
| 9 | **Major** | `src/main/java/com/meridian/payments/cl/AustralWebhookController.java:50` | Logs the entire notification, including payer name, RUT and email. | Exposes Chilean personal data in application logs, increasing privacy/compliance risk. | Log only event ID, provider, correlation ID, payment ID and safe outcome metadata; redact PII. |
| 10 | **Major** | `src/main/resources/application.yml:17` | A live-looking webhook secret is committed in plaintext. | Repository exposure can enable forged notifications; moving it in a later PR leaves the vulnerability active. | Remove from source, rotate if genuine, and inject from Key Vault/environment with managed identity and access controls. |
| 11 | **Major** | `src/main/java/com/meridian/payments/config/AsyncConfig.java:14` | Fixed thread pool has an unbounded task queue and no explicit shutdown/backpressure policy. | Month-end bursts can grow memory and delay work while webhooks still receive 200. | Prefer a durable queue and bounded consumers; define capacity, rejection, metrics, graceful shutdown and retry behavior. |
| 12 | **Major** | `src/test/java/com/meridian/payments/cl/AustralPaymentServiceTest.java:30-52` | Tests do not assert business effects; `assertNotNull(service)`, `atMost(1)`, and `assertDoesNotThrow` can pass despite lost events. | Green CI provides little protection against duplicate writes, swallowed failures and incorrect state transitions. | Assert exact repository/Salesforce interactions and outcomes; test concurrent duplicate events, failure recovery, out-of-order events, invalid/missing HMAC and the provider's signature vectors. |
| 13 | **Minor** | `src/main/java/com/meridian/payments/cl/AustralSignatureVerifier.java:27` | Compares HMAC strings with `String.equals`, not constant-time comparison. | Timing behavior can leak information about signature matches. | Decode expected/provided hex and compare using `MessageDigest.isEqual` after format validation. |
| 14 | **Minor** | `src/main/java/com/meridian/payments/cl/AustralWebhookController.java:39-50` | No explicit payload validation or controlled 400 response for malformed/missing fields. | Invalid timestamps, IDs or nested payment objects can fail late, complicating provider retries and support. | Validate required fields, ISO-8601 timestamps, event types, currency and policy format; return documented errors without logging raw payloads. |

## What looks good

- The controller, verifier and payment service have distinct responsibilities.
- The implementation attempts to respect Austral's 10-second response deadline.
- The PR introduces an explicit processed-notification table with a primary key, a useful foundation for durable idempotency after the workflow is redesigned.
- The status mapping includes confirmed, rejected and reversed payment events, although unsupported types need stricter handling.

## Scope note

The findings concern the **separate Java prototype described in PR-214**, not the Part B intake implementation. Austral is in scope for Part A design and this Part E review, but not for the Quetzal/Andina Part B implementation. No production execution or CI verification of the proposed fixes is claimed.
