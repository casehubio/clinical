CREATE TABLE protocol_deviation (
    id                 UUID         NOT NULL,
    site_id            UUID         NOT NULL,
    deviation_type     VARCHAR(255) NOT NULL,
    severity           VARCHAR(50)  NOT NULL,
    pi_approval_status VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    CONSTRAINT pk_protocol_deviation PRIMARY KEY (id),
    CONSTRAINT fk_deviation_site FOREIGN KEY (site_id) REFERENCES trial_site(id)
);
