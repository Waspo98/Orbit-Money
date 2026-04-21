-- =============================================================================
-- 016_merchant_logo_cache.sql
-- =============================================================================
-- Caches merchant-to-logo lookup decisions. Transactions keep their original
-- imported/edit provenance; this table is display enrichment only.
--
-- status:
--   candidate = provider URL has been generated, but not confirmed in browser
--   loaded    = browser successfully loaded the provider image
--   failed    = browser/provider reported the image as unavailable
--   manual    = reserved for future user-selected overrides
--   hidden    = reserved for future "always use category icon" overrides
-- =============================================================================

CREATE TABLE merchant_logo_cache (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  merchant_key    TEXT    NOT NULL UNIQUE,
  merchant_name   TEXT    NOT NULL,
  provider        TEXT    NOT NULL DEFAULT 'logo_dev',
  provider_query  TEXT    NOT NULL,
  logo_url        TEXT,
  status          TEXT    NOT NULL DEFAULT 'candidate'
                         CHECK (status IN ('candidate','loaded','failed','manual','hidden')),
  failure_count   INTEGER NOT NULL DEFAULT 0,
  last_checked_at TEXT,
  created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at      TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_merchant_logo_cache_status
  ON merchant_logo_cache(status, updated_at);
