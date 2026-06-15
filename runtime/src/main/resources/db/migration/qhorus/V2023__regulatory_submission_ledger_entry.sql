CREATE TABLE regulatory_submission_ledger_entry (
    id          UUID        NOT NULL,
    ae_id       UUID        NOT NULL,
    ctcae_grade VARCHAR(20) NOT NULL,
    filed_at    TIMESTAMP   NOT NULL,
    CONSTRAINT pk_regulatory_submission_ledger_entry
        PRIMARY KEY (id),
    CONSTRAINT fk_regulatory_submission_ledger_entry_base
        FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
