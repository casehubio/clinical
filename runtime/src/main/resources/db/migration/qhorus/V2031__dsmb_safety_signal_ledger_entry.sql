CREATE TABLE dsmb_safety_signal_ledger_entry (
    id UUID NOT NULL,
    trial_id UUID NOT NULL,
    signal_type VARCHAR(50) NOT NULL,
    affected_site_count INTEGER NOT NULL,
    summary VARCHAR(2048),
    CONSTRAINT pk_dsmb_safety_signal_ledger PRIMARY KEY (id),
    CONSTRAINT fk_dsmb_safety_signal_ledger FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
