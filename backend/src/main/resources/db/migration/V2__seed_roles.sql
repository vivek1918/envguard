-- Seed application roles only.
-- Do NOT seed a default admin with a known public password.
-- Bootstrap an administrator via ENVGUARD_BOOTSTRAP_ADMIN_USER / ENVGUARD_BOOTSTRAP_ADMIN_PASSWORD
-- or scripts/create-admin.sh after first deploy.

INSERT INTO roles (name) VALUES ('ADMINISTRATOR');
INSERT INTO roles (name) VALUES ('DEVELOPER');
INSERT INTO roles (name) VALUES ('VIEWER');
