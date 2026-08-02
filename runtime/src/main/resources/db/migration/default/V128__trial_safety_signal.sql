CREATE TABLE trial_safety_signal (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    trial_id UUID NOT NULL,
    signal_type VARCHAR(50) NOT NULL,
    affected_site_count INTEGER NOT NULL,
    summary VARCHAR(2048),
    first_detected_at TIMESTAMP NOT NULL,
    last_detected_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,
    CONSTRAINT uq_trial_safety_signal UNIQUE (tenant_id, trial_id, signal_type)
);
