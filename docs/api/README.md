# API contracts

- `webhooks.openapi.yaml` defines the public webhook routes through the enterprise API Manager.
- `events/premium-payment.schema.json` defines canonical event schema `1.0`.
- Provider payloads are authenticated over **unmodified raw request bytes** before parsing.
- New optional event properties can be introduced in a compatible minor revision only after consumers support them. The current schema is strict; deploy consumer support before producer changes.
- Breaking changes require a new major event schema and a migration period.
- Webhook paths and provider signature requirements remain provider-specific. Do not expose the internal canonical publisher as a public API.
- The provider specifications and test vectors in the original ZIP are authoritative.
