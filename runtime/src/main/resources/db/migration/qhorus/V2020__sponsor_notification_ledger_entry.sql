-- V2020: Ledger subclass join table for per-attempt sponsor notification audit.
-- JOINED inheritance from ledger_entry. Subject is notificationId (not deviationId)
-- so the per-attempt chain is isolated from the deviation's lifecycle chain.
-- deviationId stored for cross-reference: query by deviationId to find all attempts.
--
-- V2020 chosen to leave a gap above #62's renumbering range (V2000-V2009).
CREATE TABLE sponsor_notification_ledger_entry (
    id               UUID        NOT NULL PRIMARY KEY,
    notification_id  UUID        NOT NULL,
    deviation_id     UUID        NOT NULL,
    attempt_number   INT         NOT NULL,
    delivered        BOOLEAN     NOT NULL,
    failure_reason   VARCHAR(1000),
    CONSTRAINT fk_snle_ledger_entry FOREIGN KEY (id) REFERENCES ledger_entry(id)
);

CREATE INDEX idx_snle_notification_id ON sponsor_notification_ledger_entry (notification_id);
CREATE INDEX idx_snle_deviation_id    ON sponsor_notification_ledger_entry (deviation_id);
