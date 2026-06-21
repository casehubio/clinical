-- Ledger entry for IND expedited safety report filed (WorkItem completed)
-- Joins to casehub-ledger base table on the qhorus datasource.
-- V2024 and V2025 are taken by Layer 9 (eligibility_screening, protocol_amendment).
CREATE TABLE ind_report_filed_ledger_entry (
    id           UUID PRIMARY KEY REFERENCES ledger_entry(id),
    ae_id        UUID         NOT NULL,
    grade        VARCHAR(20)  NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL
);
