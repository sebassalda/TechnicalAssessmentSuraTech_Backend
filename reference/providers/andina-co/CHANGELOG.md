# Andina Pay — API changelog (excerpt)

## 2.3 — effective 2026-11-28 23:00 (Colombia time)

- **`transaccion.referencia`** is now delivered **exactly as entered or confirmed by the payer at checkout**. Previously, Andina Pay normalized the reference to the merchant's configured format before notifying. Merchants that rely on a specific format should validate and normalize the reference on their side.
- New optional field `transaccion.canal` (`WEB`, `APP`, `OFICINA`). Merchants should ignore unknown fields.

## 2.2 — 2026-06-15

- Added `transaccion.motivo_reverso` to `REVERSADA` notifications.

## 2.1 — 2026-02-02

- `X-Andina-Timestamp` header added to the signed content. The signature is now computed over `<timestamp>.<raw_body>`.
