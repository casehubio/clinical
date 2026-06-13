CREATE TABLE susar_decision_ledger_entry (
    id              UUID          NOT NULL,
    ae_id           UUID          NOT NULL,
    enrollment_id   UUID          NOT NULL,
    ctcae_grade     VARCHAR(20),
    gate_outcome    VARCHAR(20)   NOT NULL,
    decided_at      TIMESTAMP     NOT NULL,
    decided_by      VARCHAR(255),
    CONSTRAINT pk_susar_decision_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_susar_decision_ledger_entry_base FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
