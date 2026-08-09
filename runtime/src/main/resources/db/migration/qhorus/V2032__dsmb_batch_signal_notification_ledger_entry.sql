CREATE TABLE dsmb_batch_signal_notification_ledger_entry (
    id UUID NOT NULL,
    trial_id UUID NOT NULL,
    signal_type VARCHAR(50) NOT NULL,
    work_item_id UUID,
    connector_id VARCHAR(255),
    destination VARCHAR(2048),
    delivered BOOLEAN NOT NULL,
    failure_reason VARCHAR(2048),
    notified_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_dsmb_batch_signal_notif_ledger PRIMARY KEY (id),
    CONSTRAINT fk_dsmb_batch_signal_notif_ledger FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
