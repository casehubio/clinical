package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.api.model.SusarOversightStatus;

import java.time.Instant;
import java.util.UUID;

public record AdverseEventView(UUID id, UUID enrollmentId, CtcaeGrade grade,
                                 EventActuality actuality, AeOutcome outcome,
                                 String eventType, Instant occurredAt, Instant reportedAt,
                                 Instant slaDeadline, AeEscalationStatus escalationStatus,
                                 boolean unexpected, boolean suspected,
                                 SusarOversightStatus susarOversightStatus,
                                 RegulatorySubmissionStatus regulatorySubmissionStatus) {}
