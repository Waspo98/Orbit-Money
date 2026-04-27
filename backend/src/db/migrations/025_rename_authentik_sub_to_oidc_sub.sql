-- 025_rename_authentik_sub_to_oidc_sub.sql
-- Keeps existing OIDC identities while removing provider-specific naming.

ALTER TABLE users RENAME COLUMN authentik_sub TO oidc_sub;
