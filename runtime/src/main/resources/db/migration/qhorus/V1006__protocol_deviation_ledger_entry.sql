CREATE TABLE protocol_deviation_ledger_entry (
    id                     UUID         NOT NULL,
    deviation_id           UUID         NOT NULL,
    site_id                UUID         NOT NULL,
    severity               VARCHAR(50)  NOT NULL,
    pi_id                  VARCHAR(255),
    commanded_at           TIMESTAMP WITH TIME ZONE,
    response_deadline      TIMESTAMP WITH TIME ZONE,
    escalation_requirement VARCHAR(50),
    CONSTRAINT pk_pd_ledger PRIMARY KEY (id),
    CONSTRAINT fk_pd_ledger_entry FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
