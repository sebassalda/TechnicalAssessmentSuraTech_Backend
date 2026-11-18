# Austral Pagos (Chile) — Webhook specification

> Fictional provider. Excerpt of the provider's developer documentation. Chile is handled today by the squad's Java prototype (see `pr-review/`); it is **not** part of Part B, but it is part of your design in Part A.

## Delivery

- Austral sends an HTTPS `POST` with a JSON body to the merchant's endpoint.
- Respond with any `2xx` status within **10 seconds**; otherwise the event is retried up to **8 times within 48 hours**.
- Delivery is **at least once**. Retries carry the **same `eventId`** and an identical body.
- **Ordering is not guaranteed.** `payment.occurredAt` is the time of the state change.

## Signature

```
X-Austral-Signature: <signature>
```

- `<signature>` is the lowercase hexadecimal **HMAC-SHA256** of the raw body, using the merchant's webhook secret.
- Austral does **not** sign a timestamp. Use `eventId` to detect replays.

## Event types (`eventType`)

| Type | Meaning |
|---|---|
| `PAYMENT_CONFIRMED` | Payment captured. |
| `PAYMENT_REJECTED` | Payment rejected. |
| `PAYMENT_REVERSED` | A confirmed payment was reversed. |

## Payload

| Field | Type | Notes |
|---|---|---|
| `eventId` | string | Stable across retries. |
| `eventType` | string | See event types. |
| `sentAt` | string | ISO-8601 UTC. |
| `payment.paymentId` | string | Payment identifier. |
| `payment.amount` | string | Integer amount as a string. **CLP has no minor unit**: `"45990"` = CLP 45,990. |
| `payment.currency` | string | ISO 4217 (`CLP`). |
| `payment.policyNumber` | string | `POL-CL-NNNNNNNNN`. |
| `payment.occurredAt` | string | ISO-8601 UTC. Time of the state change. |
| `payment.payer` | object | Name, RUT (national ID) and email (**personal data**). |

## Test vectors

Test secret: `aus_test_whsec_2b9d6f10` (sandbox only, not valid in production).

| Body | `X-Austral-Signature` |
|---|---|
| `samples/01-payment-confirmed.json` | `ec52aeac3cbb26dbb1650a46ea82fd44c38f99dd6705e7ab413b2704e1482357` |
| `samples/02-payment-reversed.json` | `e104a041f3f5fd9bd2bcf2dc746f919fd6f759caeedc450e5f1f247e009d9721` |
