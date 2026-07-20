ALTER TABLE adverse_event ADD COLUMN trajectory_match_count INTEGER DEFAULT 0;
ALTER TABLE adverse_event ADD COLUMN trajectory_predicted_outcome VARCHAR(50);
