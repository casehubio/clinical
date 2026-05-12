CREATE TABLE patient_enrollment (
    id                UUID         NOT NULL,
    site_id           UUID         NOT NULL,
    patient_id        VARCHAR(255) NOT NULL,
    consent_status    VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    enrollment_status VARCHAR(50)  NOT NULL DEFAULT 'CANDIDATE',
    enrolled_at       TIMESTAMP,
    CONSTRAINT pk_patient_enrollment PRIMARY KEY (id),
    CONSTRAINT fk_enrollment_site FOREIGN KEY (site_id) REFERENCES trial_site(id)
);
