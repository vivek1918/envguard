-- EnvGuard core schema (MySQL 8 / H2 MySQL mode compatible)

CREATE TABLE users (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    username        VARCHAR(64)     NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE roles (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(32)     NOT NULL,
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE user_roles (
    user_id         CHAR(36)        NOT NULL,
    role_id         BIGINT          NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE projects (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(1024),
    owner_id        CHAR(36)        NOT NULL,
    default_branch  VARCHAR(128)    NOT NULL DEFAULT 'main',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT uq_projects_name UNIQUE (name)
);

CREATE INDEX idx_projects_owner ON projects (owner_id);

CREATE TABLE project_members (
    project_id      CHAR(36)        NOT NULL,
    user_id         CHAR(36)        NOT NULL,
    role            VARCHAR(32)     NOT NULL,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE repository_integrations (
    id                  CHAR(36)        NOT NULL PRIMARY KEY,
    project_id          CHAR(36)        NOT NULL,
    provider            VARCHAR(32)     NOT NULL,
    repo_url            VARCHAR(512)    NOT NULL,
    repo_owner          VARCHAR(128)    NOT NULL,
    repo_name           VARCHAR(128)    NOT NULL,
    webhook_secret_ref  VARCHAR(512),
    default_branch      VARCHAR(128)    NOT NULL DEFAULT 'main',
    external_repo_id    VARCHAR(128),
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_repo_integrations_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE INDEX idx_repo_integrations_project ON repository_integrations (project_id);

CREATE TABLE environments (
    id                          CHAR(36)        NOT NULL PRIMARY KEY,
    project_id                  CHAR(36)        NOT NULL,
    name                        VARCHAR(32)     NOT NULL,
    requires_second_approver    BOOLEAN         NOT NULL DEFAULT FALSE,
    approval_timeout_minutes    INT             NOT NULL DEFAULT 60,
    CONSTRAINT fk_environments_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_environments_project_name UNIQUE (project_id, name)
);

CREATE TABLE environment_approvers (
    environment_id  CHAR(36)        NOT NULL,
    user_id         CHAR(36)        NOT NULL,
    PRIMARY KEY (environment_id, user_id),
    CONSTRAINT fk_env_approvers_env FOREIGN KEY (environment_id) REFERENCES environments (id),
    CONSTRAINT fk_env_approvers_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE configuration_revisions (
    id                  CHAR(36)        NOT NULL PRIMARY KEY,
    project_id          CHAR(36)        NOT NULL,
    environment_id      CHAR(36)        NOT NULL,
    source_commit_sha   VARCHAR(64)     NOT NULL,
    checksum            CHAR(64)        NOT NULL,
    author_user_id      CHAR(36),
    author_identity     VARCHAR(255)    NOT NULL,
    config_yaml         LONGTEXT        NOT NULL,
    validation_status   VARCHAR(32)     NOT NULL,
    approval_status     VARCHAR(32)     NOT NULL,
    artifact_digest     VARCHAR(128),
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_config_rev_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_config_rev_env FOREIGN KEY (environment_id) REFERENCES environments (id),
    CONSTRAINT fk_config_rev_author FOREIGN KEY (author_user_id) REFERENCES users (id)
);

CREATE INDEX idx_config_rev_project_env ON configuration_revisions (project_id, environment_id);
CREATE INDEX idx_config_rev_checksum ON configuration_revisions (checksum);

CREATE TABLE configuration_variables (
    id                  BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    revision_id         CHAR(36)        NOT NULL,
    var_name            VARCHAR(128)    NOT NULL,
    var_value           VARCHAR(4096),
    is_secret_ref       BOOLEAN         NOT NULL DEFAULT FALSE,
    secret_reference    VARCHAR(512),
    secret_version      VARCHAR(128),
    CONSTRAINT fk_config_vars_revision FOREIGN KEY (revision_id) REFERENCES configuration_revisions (id),
    CONSTRAINT uq_config_vars_rev_name UNIQUE (revision_id, var_name)
);

CREATE INDEX idx_config_vars_revision ON configuration_variables (revision_id);

CREATE TABLE build_records (
    id                  CHAR(36)        NOT NULL PRIMARY KEY,
    project_id          CHAR(36)        NOT NULL,
    commit_sha          VARCHAR(64)     NOT NULL,
    branch              VARCHAR(128)    NOT NULL,
    status              VARCHAR(32)     NOT NULL,
    artifact_digest     VARCHAR(128),
    artifact_url        VARCHAR(1024),
    logs_redacted       LONGTEXT,
    started_at          TIMESTAMP       NOT NULL,
    finished_at         TIMESTAMP,
    pipeline_run_id     VARCHAR(128)    NOT NULL,
    CONSTRAINT fk_build_records_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_build_pipeline_run UNIQUE (pipeline_run_id)
);

CREATE INDEX idx_build_records_project ON build_records (project_id);
CREATE INDEX idx_build_records_commit ON build_records (commit_sha);

CREATE TABLE deployment_requests (
    id                      CHAR(36)        NOT NULL PRIMARY KEY,
    project_id              CHAR(36)        NOT NULL,
    environment_id          CHAR(36)        NOT NULL,
    revision_id             CHAR(36)        NOT NULL,
    build_id                CHAR(36)        NOT NULL,
    status                  VARCHAR(32)     NOT NULL,
    requester_identity      VARCHAR(255)    NOT NULL,
    idempotency_key         VARCHAR(128)    NOT NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at              TIMESTAMP,
    CONSTRAINT fk_deploy_req_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_deploy_req_env FOREIGN KEY (environment_id) REFERENCES environments (id),
    CONSTRAINT fk_deploy_req_revision FOREIGN KEY (revision_id) REFERENCES configuration_revisions (id),
    CONSTRAINT fk_deploy_req_build FOREIGN KEY (build_id) REFERENCES build_records (id),
    CONSTRAINT uq_deploy_req_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_deploy_req_status ON deployment_requests (status);
CREATE INDEX idx_deploy_req_project_env ON deployment_requests (project_id, environment_id);
CREATE INDEX idx_deploy_req_expires ON deployment_requests (expires_at);

CREATE TABLE approval_records (
    id                      BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    deployment_request_id   CHAR(36)        NOT NULL,
    approver_user_id        CHAR(36)        NOT NULL,
    decision                VARCHAR(32)     NOT NULL,
    comment                 VARCHAR(1024),
    revision_id             CHAR(36)        NOT NULL,
    checksum                CHAR(64)        NOT NULL,
    commit_sha              VARCHAR(64)     NOT NULL,
    artifact_digest         VARCHAR(128)    NOT NULL,
    environment_id          CHAR(36)        NOT NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_approval_deploy_req FOREIGN KEY (deployment_request_id) REFERENCES deployment_requests (id),
    CONSTRAINT fk_approval_approver FOREIGN KEY (approver_user_id) REFERENCES users (id),
    CONSTRAINT fk_approval_revision FOREIGN KEY (revision_id) REFERENCES configuration_revisions (id),
    CONSTRAINT fk_approval_env FOREIGN KEY (environment_id) REFERENCES environments (id)
);

CREATE INDEX idx_approval_deploy_req ON approval_records (deployment_request_id);

CREATE TABLE deployment_records (
    id                      CHAR(36)        NOT NULL PRIMARY KEY,
    deployment_request_id   CHAR(36)        NOT NULL,
    status                  VARCHAR(32)     NOT NULL,
    target_host             VARCHAR(255),
    health_status           VARCHAR(32),
    duration_ms             BIGINT,
    error_message_redacted  VARCHAR(2048),
    started_at              TIMESTAMP       NOT NULL,
    finished_at             TIMESTAMP,
    CONSTRAINT fk_deploy_rec_req FOREIGN KEY (deployment_request_id) REFERENCES deployment_requests (id)
);

CREATE INDEX idx_deploy_rec_req ON deployment_records (deployment_request_id);

CREATE TABLE audit_events (
    id                  CHAR(36)        NOT NULL PRIMARY KEY,
    project_id          CHAR(36),
    user_id             CHAR(36),
    environment_id      CHAR(36),
    action              VARCHAR(64)     NOT NULL,
    entity_type         VARCHAR(64)     NOT NULL,
    entity_id           VARCHAR(64),
    details_json        LONGTEXT,
    correlation_id      VARCHAR(64),
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_project ON audit_events (project_id);
CREATE INDEX idx_audit_user ON audit_events (user_id);
CREATE INDEX idx_audit_action ON audit_events (action);
CREATE INDEX idx_audit_created ON audit_events (created_at);

CREATE TABLE webhook_events (
    id                  CHAR(36)        NOT NULL PRIMARY KEY,
    provider            VARCHAR(32)     NOT NULL,
    delivery_id         VARCHAR(128)    NOT NULL,
    payload_hash        CHAR(64)        NOT NULL,
    processed_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_webhook_delivery UNIQUE (delivery_id)
);

CREATE INDEX idx_webhook_provider ON webhook_events (provider);
