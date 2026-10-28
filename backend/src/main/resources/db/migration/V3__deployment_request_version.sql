-- Real optimistic-lock column for deployment_requests (replaces broken @Transient @Version).
-- LONGTEXT already used in V1 for MySQL; H2 MySQL mode accepts LONGTEXT as well.

ALTER TABLE deployment_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
