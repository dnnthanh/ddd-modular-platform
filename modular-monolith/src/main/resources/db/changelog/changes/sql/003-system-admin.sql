-- Additive role seed: no changes to previous Liquibase changesets.
INSERT INTO auth_role(code) VALUES ('SYSTEM_ADMIN') ON CONFLICT (code) DO NOTHING;
