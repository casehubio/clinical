CREATE TABLE ae_grade_change_ledger_entry (
    id UUID PRIMARY KEY REFERENCES ledger_entry(id),
    previous_grade VARCHAR(20),
    new_grade VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    changed_by VARCHAR(255)
);
