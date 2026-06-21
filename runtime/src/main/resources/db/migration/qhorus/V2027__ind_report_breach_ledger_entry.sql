-- Ledger entry for IND reporting deadline exhausted (WorkItem ESCALATED terminal)
CREATE TABLE ind_report_breach_ledger_entry (
    id            UUID PRIMARY KEY REFERENCES ledger_entry(id),
    ae_id         UUID         NOT NULL,
    grade         VARCHAR(20)  NOT NULL,
    breached_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    breach_reason VARCHAR(255)
);
