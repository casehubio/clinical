package io.casehub.clinical.report.model;

import java.time.Instant;

public record ReportMetadata(
        String reportType,
        String tenancyId,
        Instant generatedAt,
        Instant periodStart,
        Instant periodEnd) {}
