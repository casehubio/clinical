ALTER TABLE adverse_event ADD COLUMN susar_oversight_status VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE adverse_event ADD COLUMN susar_oversight_case_id UUID;
