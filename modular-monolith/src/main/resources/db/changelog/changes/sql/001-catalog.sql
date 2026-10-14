-- PostgreSQL-only DDL. Liquibase decides whether to execute this changeset.
-- IF NOT EXISTS is an extra guard; it does not verify schema compatibility.
CREATE TABLE IF NOT EXISTS catalog_item (
  id UUID PRIMARY KEY,
  sku VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(160) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_catalog_item_created_at
  ON catalog_item(created_at DESC);
