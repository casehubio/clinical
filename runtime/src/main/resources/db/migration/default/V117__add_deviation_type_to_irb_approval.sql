-- V117: IRB domain — add deviation_type to irb_approval for CaseMemoryStore write path.
-- Nullable: existing rows have no deviation type; populated at IrbApproval creation from
-- ProtocolDeviationResolvedEvent.deviationType() going forward.
ALTER TABLE irb_approval ADD COLUMN deviation_type VARCHAR(255);
