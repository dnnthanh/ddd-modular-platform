CREATE TABLE auth_refresh_token (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES auth_user(id) ON DELETE CASCADE,
  family_id UUID NOT NULL,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_auth_refresh_token_family ON auth_refresh_token(family_id);
