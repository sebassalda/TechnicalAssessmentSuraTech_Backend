# Andina Pay (Colombia) — Webhook specification

> Fictional provider. Excerpt of the provider's developer documentation, API version **2.3** (see [`CHANGELOG.md`](CHANGELOG.md)).

## Delivery

- Andina Pay sends an HTTPS `POST` with a JSON body to the URL configured by the merchant.
- Respond with HTTP `200` or `204` within **5 seconds**. Any other status, or no response in time, is treated as a failure.
- Failed notifications are retried up to **5 times within 24 hours**.
- **Every attempt is a new notification**: retries carry a **new `notificacion_id`** and a new `fecha_notificacion`. The pair `transaccion.id` + `transaccion.estado` identifies the business event and does not change between attempts.
- A transaction usually moves `PENDIENTE` → `APROBADA` or `RECHAZADA`, and an approved transaction may later become `REVERSADA`. **Notifications may arrive out of order.** `transaccion.fecha_transaccion` is the time of the state change.

## Signature

Every request carries the headers:

```
X-Andina-Timestamp: <ISO-8601 timestamp with offset>
X-Andina-Signature: <signature>
```

- `<signature>` is the **Base64** encoding of the **HMAC-SHA256** of `<X-Andina-Timestamp value>.<raw_body>`, using the merchant's secret.
- `<raw_body>` is the request body exactly as received (bytes).
- Andina recommends rejecting requests whose timestamp differs from the current time by more than **10 minutes**.

## States (`transaccion.estado`)

| State | Meaning |
|---|---|
| `PENDIENTE` | Payment initiated, not yet confirmed (common with PSE bank transfers). |
| `APROBADA` | Payment approved and captured. |
| `RECHAZADA` | Payment rejected. |
| `REVERSADA` | A previously approved payment was reversed (bank request, chargeback). `motivo_reverso` explains why. |

## Payload

| Field | Type | Notes |
|---|---|---|
| `notificacion_id` | string | Identifier of this notification attempt. **Changes on every retry.** |
| `fecha_notificacion` | string | ISO-8601 with offset. Time this attempt was sent. |
| `transaccion.id` | string | Transaction identifier. |
| `transaccion.estado` | string | See states. |
| `transaccion.valor` | string | Decimal amount as a string, **major unit**, with two decimals (`"185000.00"` = COP 185,000). |
| `transaccion.moneda` | string | ISO 4217 (`COP`). |
| `transaccion.referencia` | string | Payment reference entered at checkout. Meridian's checkout pre-fills the policy number (`POL-CO-NNNNNNNNN`). |
| `transaccion.medio_pago` | string | `PSE`, `TARJETA`, `EFECTIVO`. |
| `transaccion.fecha_transaccion` | string | ISO-8601 with offset. Time of the state change. |
| `transaccion.motivo_reverso` | string | Only for `REVERSADA`. |
| `transaccion.pagador` | object | Payer's name, national ID document and email (**personal data**). |

## Test vectors

Test secret: `andina_test_secret_7c3e19ab` (sandbox only, not valid in production).
Each sample file contains the exact raw body (no trailing newline).

| Body | `X-Andina-Timestamp` | `X-Andina-Signature` |
|---|---|---|
| `samples/01-transaccion-pendiente.json` | `2026-11-28T14:02:59-05:00` | `JhaBZfk7eQsoMbbQbThmAx/bCyehBOzKYw8XTP2a40k=` |
| `samples/02-transaccion-aprobada.json` | `2026-11-28T14:03:11-05:00` | `MFQjfviySi6CjqbWtQcy5spKtXxHIxAvmg5W3PTzPJk=` |
| `samples/03-transaccion-aprobada-reintento.json` | `2026-11-28T14:08:11-05:00` | `CNu6m5X6B9bMKTsi/IzZfGquIh/KdxTyesBtL4ax424=` |
| `samples/04-transaccion-reversada.json` | `2026-12-03T09:15:42-05:00` | `AfGO+MEffk0AWIEEE3cgyff4hWewoOaDhygCWxs/fNI=` |
