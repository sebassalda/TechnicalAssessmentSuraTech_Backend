CREATE TABLE IF NOT EXISTS intake_inbox (
 idempotency_key VARCHAR(250) PRIMARY KEY,
 fingerprint TEXT NOT NULL,
 accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS intake_outbox (
 id BIGSERIAL PRIMARY KEY,
 idempotency_key VARCHAR(250) NOT NULL UNIQUE REFERENCES intake_inbox(idempotency_key),
 payload JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 published_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_intake_outbox_pending ON intake_outbox(id) WHERE published_at IS NULL;
