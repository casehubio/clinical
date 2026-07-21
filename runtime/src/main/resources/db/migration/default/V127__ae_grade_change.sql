CREATE TABLE ae_grade_change (
    id UUID PRIMARY KEY,
    adverse_event_id UUID NOT NULL,
    previous_grade VARCHAR(20),
    new_grade VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    changed_by VARCHAR(255) NOT NULL,
    reason VARCHAR(500),
    CONSTRAINT fk_ae_grade_change_ae FOREIGN KEY (adverse_event_id)
        REFERENCES adverse_event(id)
);
CREATE INDEX idx_ae_grade_change_ae_id ON ae_grade_change(adverse_event_id);

INSERT INTO ae_grade_change (id, adverse_event_id, previous_grade, new_grade, changed_at, changed_by, reason)
SELECT gen_random_uuid(), id, NULL, grade, reported_at, 'migration', 'Retroactive initial grade entry'
FROM adverse_event
WHERE NOT EXISTS (
    SELECT 1 FROM ae_grade_change gc WHERE gc.adverse_event_id = adverse_event.id
);
