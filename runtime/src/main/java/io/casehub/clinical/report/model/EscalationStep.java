package io.casehub.clinical.report.model;

import java.time.Instant;

public record EscalationStep(
        String status,
        Instant occurredAt,
        String actorId) {}
