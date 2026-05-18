ALTER TABLE protocol_deviation_ledger_entry
    ADD COLUMN terminal_status VARCHAR(50),
    ADD COLUMN resolved_at     TIMESTAMP WITH TIME ZONE;
