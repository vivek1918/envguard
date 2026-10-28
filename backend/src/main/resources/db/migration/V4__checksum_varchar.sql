-- Hibernate ddl-auto=validate expects VARCHAR for String(length=64), not CHAR.
-- MariaDB/MySQL report CHAR differently from VARCHAR during schema validation.
ALTER TABLE configuration_revisions MODIFY checksum VARCHAR(64) NOT NULL;
ALTER TABLE approval_records MODIFY checksum VARCHAR(64) NOT NULL;
ALTER TABLE webhook_events MODIFY payload_hash VARCHAR(64) NOT NULL;
