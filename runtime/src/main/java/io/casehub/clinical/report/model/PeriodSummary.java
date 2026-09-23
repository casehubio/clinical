package io.casehub.clinical.report.model;

import io.casehub.clinical.api.model.CtcaeGrade;
import java.util.Map;

public record PeriodSummary(
        int totalAdverseEvents,
        Map<CtcaeGrade, Integer> byGrade,
        int susarCount,
        int indReportsFiled,
        int indReportsBreached,
        int seriousUnexpectedCount) {}
