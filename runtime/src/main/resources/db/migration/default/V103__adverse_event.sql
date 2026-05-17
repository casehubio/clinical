CREATE TABLE adverse_event (
    id            UUID        NOT NULL,
    enrollment_id UUID        NOT NULL,
    grade         VARCHAR(50) NOT NULL,
    actuality     VARCHAR(50) NOT NULL DEFAULT 'ACTUAL',
    outcome       VARCHAR(50) NOT NULL DEFAULT 'ONGOING',
    occurred_at   TIMESTAMP   NOT NULL,
    reported_at   TIMESTAMP   NOT NULL,
    sla_deadline  TIMESTAMP,
    CONSTRAINT pk_adverse_event PRIMARY KEY (id),
    CONSTRAINT fk_ae_enrollment FOREIGN KEY (enrollment_id) REFERENCES patient_enrollment(id)
);
