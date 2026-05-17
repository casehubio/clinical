CREATE TABLE clinical_trial (
    id                 UUID         NOT NULL,
    protocol_id        VARCHAR(255) NOT NULL,
    phase              VARCHAR(50)  NOT NULL,
    sponsor            VARCHAR(255) NOT NULL,
    target_enrollment  INTEGER      NOT NULL,
    status             VARCHAR(50)  NOT NULL DEFAULT 'PLANNING',
    CONSTRAINT pk_clinical_trial PRIMARY KEY (id)
);
