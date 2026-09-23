package io.casehub.clinical.report.model;

import java.time.Instant;

public record SusarDecision(
        String gateOutcome,
        Instant decidedAt) {}
