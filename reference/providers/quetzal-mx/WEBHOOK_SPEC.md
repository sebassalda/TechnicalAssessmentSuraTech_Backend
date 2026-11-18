# Quetzal Pagos (Mexico) — Webhook specification

> Fictional provider. Excerpt of the provider's developer documentation, API version `2025-06-01`.

## Delivery

- Quetzal sends an HTTPS `POST` with a JSON body to the endpoint registered by the merchant.
- Respond with any `2xx` status within **10 seconds**. Any other status, or no response in time, is treated as a failure.
- Failed deliveries are retried with exponential backoff for up to **3 days**. After that the event is marked as failed in the Quetzal dashboard and is not sent again automatically.
- Delivery is **at least once**: the same event may be delivered more than once. A retried delivery carries the **same `id`** and an identical body.
- **Ordering is not guaranteed.** Use `created` (event time) and the charge timestamps to reason about order.

## Signature

Every request carries the header:

```
Quetzal-Signature: t=<unix_timestamp>,v1=<signature>
```

- `<signature>` is the lowercase hexadecimal **HMAC-SHA256** of the string `<unix_timestamp>.<raw_body>`, using the merchant's webhook secret.
- `<raw_body>` is the request body **exactly as received** (bytes). Do not re-serialize the JSON before computing the signature.
- Reject the request if `|now − t|` is greater than **300 seconds**.
- During secret rotation the header may carry two signatures (`v1=...,v1=...`). The request is valid if **any** of them matches.
- Compare signatures with a constant-time comparison.

## Event types

| `type` | Meaning |
|---|---|
| `charge.succeeded` | The charge was captured. |
| `charge.failed` | The charge attempt failed (`failure_code` explains why). |
| `charge.refunded` | The charge was refunded, totally or partially (`amount_refunded`). |

## Payload

| Field | Type | Notes |
|---|---|---|
| `id` | string | Event identifier. Stable across retries. |
| `type` | string | See event types. |
| `created` | integer | Event time, Unix seconds (UTC). |
| `data.charge.id` | string | Charge identifier. Several events refer to the same charge. |
| `data.charge.amount` | integer | Amount in the **minor unit** of the currency (centavos for MXN). `125000` = MXN 1,250.00. |
| `data.charge.amount_refunded` | integer | Only in `charge.refunded`. Minor unit. |
| `data.charge.currency` | string | ISO 4217, **lowercase** (`mxn`). |
| `data.charge.reference` | string | Merchant reference. Meridian sends the policy number (`POL-MX-NNNNNNNNN`). |
| `data.charge.paid_at`, `refunded_at` | integer \| null | Unix seconds (UTC). |
| `data.charge.payment_method` | object | Type and payment **token** only. Never card data. |
| `data.charge.customer.email` | string | Payer's email (personal data). |

## Test vectors

Test secret: `qz_test_whsec_4f8a1c2e9b7d` (sandbox only, not valid in production).
Each sample file contains the exact raw body (no trailing newline).

| Body | `t` | `Quetzal-Signature` header |
|---|---|---|
| `samples/01-charge-succeeded.json` | `1795888803` | `t=1795888803,v1=2d07770582c02f15235ac4e027b7732beef86be103c574a1c3b988a4275f5031` |
| `samples/02-charge-failed.json` | `1795889403` | `t=1795889403,v1=d793d3da0a8d4d66e984ada3daa8b9f473a0eef153d99cdd83bb70f60f13ccc9` |
| `samples/03-charge-refunded.json` | `1795975203` | `t=1795975203,v1=a61f9d88d861cd4cc63359b5ca6e6d11d55369db7079387dee9a5024ccb2cae6` |
