package io.casehub.clinical.api.view;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntryView(
        UUID id, UUID subjectId, int sequenceNumber,
        String entryType, String actorId, String actorRole,
        Instant occurredAt, String digest, String summary
) {}
