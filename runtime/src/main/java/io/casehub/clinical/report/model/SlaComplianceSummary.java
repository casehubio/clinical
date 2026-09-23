package io.casehub.clinical.report.model;

public record SlaComplianceSummary(
        int totalObligations,
        int metWithinSla,
        int breached,
        double complianceRate) {}
