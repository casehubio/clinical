CREATE TABLE protocol_amendment_ledger_entry (
    id                        UUID    NOT NULL,
    amendment_id              UUID    NOT NULL,
    trial_id                  UUID    NOT NULL,
    proposed_change           TEXT    NOT NULL,
    status                    VARCHAR(50) NOT NULL,
    supervisor_recommendation VARCHAR(50),
    CONSTRAINT pk_protocol_amendment_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_protocol_amendment_ledger_entry_base
        FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
