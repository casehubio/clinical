ALTER TABLE trial_safety_signal ADD COLUMN work_item_id UUID;
CREATE UNIQUE INDEX idx_trial_safety_signal_unique
    ON trial_safety_signal(trial_id, signal_type, tenant_id);
