CREATE TABLE ae_ledger_entry (
    id              UUID    NOT NULL,
    adverse_event_id UUID   NOT NULL,
    enrollment_id   UUID    NOT NULL,
    ctcae_grade     VARCHAR(20) NOT NULL,
    reported_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    sla_deadline    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ae_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_ae_ledger_entry_base FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
