ALTER TABLE protocol_deviation
    ADD COLUMN pi_command_channel_name VARCHAR(500),
    ADD COLUMN commanded_at           TIMESTAMP WITH TIME ZONE,
    ADD COLUMN response_deadline      TIMESTAMP WITH TIME ZONE,
    ADD COLUMN escalation_requirement VARCHAR(50);
