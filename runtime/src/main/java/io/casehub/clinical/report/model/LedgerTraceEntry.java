package io.casehub.clinical.report.model;

import java.time.Instant;

public record LedgerTraceEntry(
        String entryType,
        Instant occurredAt,
        String actorId,
        String digest,
        long sequenceNumber) {}
