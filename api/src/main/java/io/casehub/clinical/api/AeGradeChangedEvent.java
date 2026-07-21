package io.casehub.clinical.api;

import io.casehub.clinical.api.model.CtcaeGrade;

import java.time.Instant;
import java.util.UUID;

public record AeGradeChangedEvent(
    UUID aeId,
    UUID enrollmentId,
    UUID siteId,
    CtcaeGrade previousGrade,
    CtcaeGrade newGrade,
    Instant changedAt,
    String changedBy,
    String tenantId
) {
    public boolean isUpgrade() {
        return previousGrade == null || newGrade.ordinal() > previousGrade.ordinal();
    }

    public boolean isDowngrade() {
        return previousGrade != null && newGrade.ordinal() < previousGrade.ordinal();
    }
}
