package io.casehub.clinical.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.clinical.api.AeEscalationCompletedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.UUID;

/**
 * Observes case lifecycle events and handles AE escalation case completion.
 *
 * <p>Discriminates AE escalation cases by presence of {@code aeId} in the case
 * context snapshot (set at case start by AeEscalationCaseService). Deviation review
 * cases and other cases lack this key and are silently ignored.
 */
@ApplicationScoped
public class AeEscalationListener {

    private static final Logger LOG = Logger.getLogger(AeEscalationListener.class);
    static final String OUTCOME_KEY = "outcome";

    @Inject AeEscalationLedgerWriter ledgerWriter;
    @Inject AeStatusUpdater statusUpdater;
    @Inject Event<AeEscalationCompletedEvent> completedEvents;
    @Inject io.casehub.clinical.memory.ClinicalMemoryService memoryService;
    @Inject io.casehub.clinical.cbr.AeTrajectoryAlertService aeTrajectoryAlertService;

    public void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        LOG.debugf("AeEscalationListener: received eventType=%s caseStatus=%s caseId=%s", event.eventType(), event.caseStatus(), event.caseId());
        if (!"GoalReached".equals(event.eventType()) && !"CaseCompleted".equals(event.eventType())) return;

        JsonNode snapshot = event.contextSnapshot();
        if (snapshot == null) return;

        String aeIdStr = snapshot.path("aeId").asText(null);
        if (aeIdStr == null) return;

        UUID aeId;
        try {
            aeId = UUID.fromString(aeIdStr);
        } catch (IllegalArgumentException e) {
            LOG.warnf("AeEscalationListener: invalid aeId in case context: %s", aeIdStr);
            return;
        }

        boolean firstCompletion = statusUpdater.markCompleted(aeId);
        if (!firstCompletion) return;
        try { aeTrajectoryAlertService.evaluate(aeId, event.tenancyId()); } catch (Exception te) { LOG.warnf(te, "Trajectory alert evaluation failed for aeId=%s", aeId); }

        UUID enrollmentId = resolveUuid(snapshot.path("enrollmentId").asText(null));
        if (enrollmentId == null) {
            LOG.warnf("AeEscalationListener: enrollmentId missing from case context for aeId=%s — ledger write skipped", aeId);
            return;
        }
        UUID siteId = resolveUuid(snapshot.path("siteId").asText(null));
        CtcaeGrade grade = resolveGrade(snapshot.path("grade").asText(null));
        String safetyReviewOutcome = snapshot.path("safetyReview").path(OUTCOME_KEY).asText(null);
        boolean dsmbEscalated = !snapshot.path("dsmbEscalation").isMissingNode()
                && !snapshot.path("dsmbEscalation").isNull();
        boolean unexpected = snapshot.path("unexpected").asBoolean(false);
        Instant completedAt = Instant.now();

        boolean ledgerWritten = false;
        try {
            ledgerWriter.writeCompletionEntry(aeId, enrollmentId, grade, safetyReviewOutcome, dsmbEscalated, completedAt);
            ledgerWritten = true;
            String tenantId = snapshot.path("tenantId").asText(null);
            if (tenantId != null) {
                memoryService.storeAeOutcome(aeId, enrollmentId, grade, safetyReviewOutcome, dsmbEscalated, tenantId);
            }
            completedEvents.fireAsync(new AeEscalationCompletedEvent(
                    aeId, grade, siteId, safetyReviewOutcome, dsmbEscalated, completedAt, unexpected));
        } catch (Exception e) {
            if (!ledgerWritten) {
                LOG.errorf(e, "AeEscalationListener: unexpected error for aeId=%s (enrollmentId=%s, grade=%s) — writing failure entry", aeId, enrollmentId, grade);
                try {
                    ledgerWriter.writeObserverFailureEntry(aeId, enrollmentId, grade);
                } catch (Exception writeEx) {
                    LOG.errorf(writeEx, "AUDIT GAP: could not write observer failure entry for aeId=%s", aeId);
                }
            } else {
                LOG.errorf(e, "AeEscalationListener: downstream fireAsync failed for aeId=%s — ledger entry exists, no fallback needed", aeId);
            }
        }
    }

    private UUID resolveUuid(String str) {
        if (str == null) return null;
        try { return UUID.fromString(str); } catch (IllegalArgumentException e) { return null; }
    }

    private CtcaeGrade resolveGrade(String str) {
        if (str == null) return null;
        try { return CtcaeGrade.valueOf(str); } catch (IllegalArgumentException e) { return null; }
    }
}
