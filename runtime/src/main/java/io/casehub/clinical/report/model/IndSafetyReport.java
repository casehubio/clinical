package io.casehub.clinical.report.model;

import java.util.List;

public record IndSafetyReport(
        ReportMetadata metadata,
        TrialReportContext trial,
        PeriodSummary period,
        List<IndividualCaseSafetyReport> icsrs,
        SlaComplianceSummary slaCompliance) {}
