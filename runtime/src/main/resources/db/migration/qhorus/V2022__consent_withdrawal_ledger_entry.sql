CREATE TABLE consent_withdrawal_ledger_entry (
    id                      UUID        NOT NULL,
    enrollment_id           UUID        NOT NULL,
    withdrawn_at            TIMESTAMP   NOT NULL,
    ledger_entries_affected BIGINT      NOT NULL DEFAULT 0,
    memories_erased         BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_consent_withdrawal_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_consent_withdrawal_ledger_entry_base FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
