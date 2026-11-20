# Part B — Payment Webhook Intake (Java 21 / Spring Boot 3.4.4)

## Scope
Quetzal MX and Andina CO only. Verify HMAC over **exact raw bytes**, reject stale timestamps, normalize to canonical v1.0, atomically persist inbox+outbox, and publish to a **stub** topic. No Salesforce writes. No real Azure Service Bus or secrets manager.

## Prerequisites (Windows/macOS/Linux)
- Java **21**, Maven **3.9+**, Docker Desktop with Compose, Postman (current version).
- `java -version`, `mvn -version`, `docker compose version`.

## Start locally
From the repository root:

```bash
cd practical
docker compose up -d
# PowerShell:
$env:QUETZAL_WEBHOOK_SECRET="qz_test_whsec_4f8a1c2e9b7d"
$env:ANDINA_WEBHOOK_SECRET="andina_test_secret_7c3e19ab"
mvn clean test
mvn spring-boot:run
```

On macOS/Linux use `export QUETZAL_WEBHOOK_SECRET=...` and `export ANDINA_WEBHOOK_SECRET=...` instead. Port 8080 must be free; PostgreSQL binds only to `127.0.0.1:5432`. Schema is initialized on startup.

## Postman
Import `practical/postman/Meridian_PartB.postman_collection.json` and `practical/postman/Local.postman_environment.json`. Select **Meridian Local**. Send the provider sample requests. Pre-request scripts generate **fresh timestamps and HMAC** using `pm.require('npm:crypto-js@4.2.0')`; modern Postman with package support is required. The samples have fixed signatures dated Nov/Dec 2026 and will fail replay-window validation if sent unchanged. Never pretty-print/reformat signed request bodies after signing.

Then run `GET {{baseUrl}}/local/status` to see inbox/pending/published counters; dispatcher automatically publishes to a stub every second. `POST /local/broker/fail-next` simulates one failed send; `POST /local/dispatch` triggers a manual dispatch. Diagnostics endpoints are **local testing only**, not intended for API Manager or production.

If Postman package imports are unavailable, run the dependency-free helper from the repository root:

```bash
python practical/postman/sign_and_send.py quetzal reference/providers/quetzal-mx/samples/01-charge-succeeded.json
python practical/postman/sign_and_send.py andina reference/providers/andina-co/samples/02-transaccion-aprobada.json
```

## Local restart / deduplication
Send an accepted webhook; restart Spring Boot (leave Docker running); send the **same sample** again with a newly generated signature. Inbox count must not increase. For Andina, `02-transaccion-aprobada.json` and `03-transaccion-aprobada-reintento.json` represent the same business event: one inbox row.

## Storage and stubs
- `postgres` (default): PostgreSQL 16 via local Docker, transactionally stores inbox identity and canonical outbox. No raw body or payer PII persisted.
- `stub`: in-memory adapter for unit tests; **not crash durable**. Use `--spring.profiles.active=stub` for isolated local demo without PostgreSQL.
- Event publisher: in-memory stub; no real broker calls. Secrets: environment variables, not an external vault. All dependencies behind interfaces.
- Outbox publishes at least once; after a crash between stub publication and marking sent, a replay is possible, so downstream consumers must deduplicate by `idempotencyKey`.

## Limitations / important distinction
The assessment asks that **every external dependency be stubbed**. PostgreSQL is provided as an **optional local durability demonstration**, not a required real cloud integration. The default profile uses local PostgreSQL so Postman can exercise persistence; the `stub` profile keeps the assessment-compatible isolated adapter.

The in-memory stub broker loses its delivered-message list on restart and is **not a durable real queue**. Consequently, this is not proof of NFR3 end-to-end under all crash scenarios. The PostgreSQL outbox retains unpublished messages; replacing the publisher with a durable broker and end-to-end failure testing are production follow-ups. The dispatcher is single-instance oriented; multi-replica deployments need database claiming/locking or leasing and retry scheduling. No actual Salesforce calls or Chile intake are implemented.

## Webhook acknowledgment response (JSON)

Both webhook endpoints return HTTP 200 with a structured JSON body for newly accepted
notifications and identical duplicates. `code` is `WEBHOOK_ACCEPTED` or
`WEBHOOK_DUPLICATE`; `transactionId` is the provider payment/transaction ID,
`providerEventId` is the incoming notification ID, and `status` is the
normalized state (`Pending`, `Captured`, `Failed`, `Refunded`).
`originalStatus` preserves the provider's state. `processingStatus` reports
`PENDING_PUBLICATION` for a new event and `ALREADY_ACCEPTED` for duplicates.
`statusChange` is always `NOT_DETERMINED`: Intake does not maintain a durable
previous payment status and cannot assert that Salesforce was updated.

Errors use HTTP 400 (`INVALID_PAYLOAD`), 401 (`INVALID_SIGNATURE`), 409
(`IDEMPOTENCY_CONFLICT`), or 503 (`PERSISTENCE_UNAVAILABLE`) with JSON bodies.
Invalid-signature responses deliberately omit untrusted payment identifiers.
No change to Postman request bodies, headers, or pre-request HMAC scripts is needed.
