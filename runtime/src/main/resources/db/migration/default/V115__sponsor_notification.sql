-- V115: Durable sponsor notification entity for retry tracking.
-- GCP compliance: persists every delivery attempt with status, timestamps, and
-- a full payload snapshot so retries do not re-resolve PI identity or connector config.
CREATE TABLE sponsor_notification (
    id                  UUID        NOT NULL PRIMARY KEY,
    deviation_id        UUID        NOT NULL,
    trial_id            UUID        NOT NULL,
    site_id             UUID        NOT NULL,
    -- Future multi-tenancy: nullable, null = single-tenant deployment
    tenant_id           VARCHAR(64),
    status              VARCHAR(32) NOT NULL,
    attempts            INT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMP   NOT NULL,
    last_attempted_at   TIMESTAMP,
    next_retry_after    TIMESTAMP,
    delivered_at        TIMESTAMP,
    failure_reason      VARCHAR(1000),
    -- Payload snapshot — resolved at notify() time; used verbatim on retry
    pi_id               VARCHAR(255),
    pi_display_name     VARCHAR(255),
    connector_id        VARCHAR(128) NOT NULL,
    destination         VARCHAR(2048) NOT NULL,
    severity            VARCHAR(32)  NOT NULL,
    terminal_status     VARCHAR(32)  NOT NULL,
    deviation_type      VARCHAR(128) NOT NULL
);

-- Scheduler query: status IN ('PENDING','FAILED') AND (next_retry_after IS NULL OR next_retry_after <= now())
CREATE INDEX idx_sn_eligible ON sponsor_notification (status, next_retry_after);
