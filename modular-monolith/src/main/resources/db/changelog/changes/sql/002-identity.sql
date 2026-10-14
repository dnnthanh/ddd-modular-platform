-- Initial schema and permission seeds for a fresh database (not yet applied).
CREATE TABLE IF NOT EXISTS auth_user (
  id UUID PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS auth_role (
  code VARCHAR(64) PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS auth_permission (
  code VARCHAR(100) PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS auth_user_role (
  user_id UUID NOT NULL REFERENCES auth_user(id) ON DELETE CASCADE,
  role_code VARCHAR(64) NOT NULL REFERENCES auth_role(code),
  PRIMARY KEY (user_id, role_code)
);

CREATE TABLE IF NOT EXISTS auth_role_permission (
  role_code VARCHAR(64) NOT NULL REFERENCES auth_role(code),
  permission_code VARCHAR(100) NOT NULL REFERENCES auth_permission(code),
  PRIMARY KEY (role_code, permission_code)
);

INSERT INTO auth_role(code)
VALUES ('ADMIN')
ON CONFLICT (code) DO NOTHING;

INSERT INTO auth_permission(code)
VALUES ('CATALOG_READ'), ('CATALOG_WRITE'), ('USERS_READ'), ('USERS_MANAGE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO auth_role_permission(role_code, permission_code)
SELECT 'ADMIN', code FROM auth_permission
ON CONFLICT (role_code, permission_code) DO NOTHING;
