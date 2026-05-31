-- V1012: allow null ctcae_grade in ae_escalation_ledger_entry for observer failure entries.
-- When AeEscalationListener throws before resolving grade from case context,
-- null is semantically correct (grade indeterminate at failure time).
ALTER TABLE ae_escalation_ledger_entry ALTER COLUMN ctcae_grade DROP NOT NULL;
