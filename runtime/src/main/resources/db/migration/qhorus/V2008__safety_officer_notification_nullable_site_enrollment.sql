-- V1013: allow null site_id and enrollment_id in safety_officer_notification_ledger_entry.
-- Skipped-delivery entries (e.g. site not found, no site_id in event) have no meaningful
-- site or enrollment to record; NOT NULL previously blocked audit writes for these paths.
ALTER TABLE safety_officer_notification_ledger_entry ALTER COLUMN site_id DROP NOT NULL;
ALTER TABLE safety_officer_notification_ledger_entry ALTER COLUMN enrollment_id DROP NOT NULL;
