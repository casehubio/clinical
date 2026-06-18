CREATE TABLE protocol_amendment (
    id                      UUID            NOT NULL,
    tenant_id               VARCHAR(255)    NOT NULL,
    trial_id                UUID            NOT NULL,
    proposed_change         TEXT            NOT NULL,
    status                  VARCHAR(50)     NOT NULL DEFAULT 'PROPOSED',
    amendment_case_status   VARCHAR(50)     NOT NULL DEFAULT 'NONE',
    supervisor_recommendation VARCHAR(50),
    engine_case_id          UUID,
    proposed_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_protocol_amendment PRIMARY KEY (id)
);
