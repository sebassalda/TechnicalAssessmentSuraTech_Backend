# Salesforce — API constraints for the Payments Integration

> Fictional figures agreed between the Salesforce team and the Payments Integration squad.

## Availability and latency

| Metric | Value |
|---|---|
| Availability (monthly) | ≈ **97%**. Unavailability is unplanned and usually comes in windows of 5 to 90 minutes. |
| Latency, single-record upsert | p50 ≈ 300 ms · p95 ≈ 1.2 s · up to 8 s under load |
| Latency, 200-record collection upsert | p50 ≈ 1.5 s · p95 ≈ 4 s |
| Concurrency | Maximum **25 concurrent requests** from the integration user (agreed with the Salesforce team). |

## API budget

- This integration may use up to **150,000 API calls per rolling 24 hours**. Every REST request counts as one call, regardless of how many records it carries.
- The org-wide daily limit is **shared** by every integration of the org, including contact-center and partner integrations. When the org-wide limit is exceeded, Salesforce rejects requests from **all** integrations with `403` and error code `REQUEST_LIMIT_EXCEEDED` until usage falls back under the limit.
- The Salesforce team monitors usage per integration user and may suspend an integration that exceeds its budget.

## Authentication

- OAuth 2.0 **client credentials** flow through an external client app registered in the org. The client secret is issued by the Salesforce team.
- Access tokens are valid for about 2 hours. Cache and reuse them; requesting a token per call wastes budget and may be throttled.

## Error behavior

| Status | Error code examples | Typical cause | Retry? |
|---|---|---|---|
| `400` | `INVALID_FIELD`, `FIELD_CUSTOM_VALIDATION_EXCEPTION`, `REQUIRED_FIELD_MISSING` | The data is invalid for the org | Not without fixing the data |
| `401` | `INVALID_SESSION_ID` | Expired or invalid token | After obtaining a new token |
| `403` | `REQUEST_LIMIT_EXCEEDED` | API limit exceeded | Only after usage recovers |
| `400` | `UNABLE_TO_LOCK_ROW` | Concurrent updates to the same record | Yes, with backoff |
| `500`, `502`, `503` | — | Salesforce unavailable or degraded | Yes, with backoff |
