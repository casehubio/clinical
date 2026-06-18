CREATE TABLE eligibility_screening_ledger_entry (
    id                UUID    NOT NULL,
    enrollment_id     UUID    NOT NULL,
    screening_result  VARCHAR(50) NOT NULL,
    criteria_count    INT     NOT NULL,
    marginal_count    INT     NOT NULL,
    CONSTRAINT pk_eligibility_screening_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_eligibility_screening_ledger_entry_base
        FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
