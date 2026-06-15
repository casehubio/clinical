ALTER TABLE adverse_event
    ADD COLUMN regulatory_submission_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN regulatory_submission_case_id UUID;
