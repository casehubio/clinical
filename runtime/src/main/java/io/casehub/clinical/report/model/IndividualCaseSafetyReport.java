package io.casehub.clinical.report.model;

import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IndividualCaseSafetyReport(
        UUID aeId,
        UUID patientEnrollmentId,
        CtcaeGrade grade,
        String eventType,
        Instant reportedAt,
        Instant slaDeadline,
        boolean unexpected,
        boolean suspected,
        AeOutcome outcome,
        RegulatorySubmissionStatus regulatoryStatus,
        List<EscalationStep> escalationChain,
        @Nullable SusarDecision susarDecision,
        List<LedgerTraceEntry> auditTrail) {}
