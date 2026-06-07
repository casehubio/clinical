-- GCP compliance: record the formal PI name used in sponsor notifications
-- alongside the system actorId, so FDA auditors can reconstruct notification content.
ALTER TABLE protocol_deviation_ledger_entry
    ADD COLUMN pi_display_name VARCHAR(255);
