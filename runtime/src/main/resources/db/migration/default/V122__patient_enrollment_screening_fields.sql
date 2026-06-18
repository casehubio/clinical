ALTER TABLE patient_enrollment
    ADD COLUMN screening_result                VARCHAR(50),
    ADD COLUMN screening_completed_at          TIMESTAMP WITH TIME ZONE,
    ADD COLUMN eligibility_engine_case_id      UUID,
    ADD COLUMN eligibility_screening_case_status VARCHAR(50) NOT NULL DEFAULT 'NONE';
