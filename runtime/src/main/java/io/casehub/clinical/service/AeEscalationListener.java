package io.casehub.clinical.service;

import io.casehub.clinical.api.AeEscalationCompletedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Observes case lifecycle events and handles AE escalation case completion.
 *
 * <p>Discriminates AE escalation cases by presence of {@code aeId} in the case
 * context (set at case start by AeEscalationCaseService). Deviation review cases
 * and other cases lack this key and are silently ignored.
 *
 * <p>{@code CaseLifecycleEvent} is from {@code io.casehub.engine.common.spi.event}
 * — the public SPI package promoted in engine#378 (was internal.event).
 */
@ApplicationScoped
public class AeEscalationListener {

    private static final Logger LOG = Logger.getLogger(AeEscalationListener.class);
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(5);
    /** Key written by the AE escalation YAML binding's outputMapping: "{ safetyReview: . }". */
    static final String OUTCOME_KEY = "outcome";

    @Inject CaseInstanceRepository caseInstanceRepository;
    @Inject AeEscalationLedgerWriter ledgerWriter;
    @Inject AeStatusUpdater statusUpdater;
    @Inject Event<AeEscalationCompletedEvent> completedEvents;

    @Transactional
    public void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        LOG.debugf("AeEscalationListener: received eventType=%s caseStatus=%s caseId=%s", event.eventType(), event.caseStatus(), event.caseId());
        // React to GoalReached (which the engine fires when all goals are met, before status updates).
        // CaseCompleted is also fired by CaseStatusChangedHandler but may not reliably arrive in all environments.
        if (!"GoalReached".equals(event.eventType()) && !"CaseCompleted".equals(event.eventType())) return;

        var instance = caseInstanceRepository
                .findByUuid(event.caseId())
                .await().atMost(LOOKUP_TIMEOUT);
        if (instance == null) return;

        Object aeIdObj = instance.getCaseContext().getPath("aeId");
        if (aeIdObj == null) return; // not an AE escalation case

        UUID aeId;
        try {
            aeId = UUID.fromString(aeIdObj.toString());
        } catch (IllegalArgumentException e) {
            LOG.warnf("AeEscalationListener: invalid aeId in case context: %s", aeIdObj);
            return;
        }

        // Write COMPLETED before enrollmentId check — status reflects case completion
        // regardless of whether context is complete enough for ledger write.
        // REQUIRES_NEW in AeStatusUpdater ensures this commits even if the outer transaction rolls back.
        // Returns false if already COMPLETED — GoalReached fires multiple times per case (idempotency guard).
        boolean firstCompletion = statusUpdater.markCompleted(aeId);
        if (!firstCompletion) {
            return; // already handled by a prior GoalReached event for this case
        }

        UUID enrollmentId = resolveUuid(instance.getCaseContext().getPath("enrollmentId"));
        if (enrollmentId == null) {
            LOG.warnf("AeEscalationListener: enrollmentId missing from case context for aeId=%s — ledger write skipped", aeId);
            return;
        }

        UUID siteId = resolveUuid(instance.getCaseContext().getPath("siteId"));
        CtcaeGrade grade = resolveGrade(instance.getCaseContext().getPath("grade"));
        // safetyReview is the full WorkItem resolution mapped by outputMapping: "{ safetyReview: . }"
        // The resolution body must include an "outcome" field — e.g. {"outcome":"REVIEWED","reviewedAt":"..."}
        String safetyReviewOutcome = resolveOutcome(instance.getCaseContext().getPath("safetyReview"));
        boolean dsmbEscalated = instance.getCaseContext().getPath("dsmbEscalation") != null;
        Instant completedAt = Instant.now();

        ledgerWriter.writeCompletionEntry(aeId, enrollmentId, grade, safetyReviewOutcome, dsmbEscalated, completedAt);

        completedEvents.fireAsync(new AeEscalationCompletedEvent(
                aeId, grade, siteId, safetyReviewOutcome, dsmbEscalated, completedAt));
    }

    private UUID resolveUuid(Object obj) {
        if (obj == null) return null;
        try { return UUID.fromString(obj.toString()); } catch (IllegalArgumentException e) { return null; }
    }

    private CtcaeGrade resolveGrade(Object obj) {
        if (obj == null) return null;
        try { return CtcaeGrade.valueOf(obj.toString()); } catch (IllegalArgumentException e) { return null; }
    }

    private String resolveOutcome(Object obj) {
        if (!(obj instanceof Map<?, ?> map)) return null;
        Object outcome = map.get(OUTCOME_KEY);
        return outcome != null ? outcome.toString() : null;
    }
}
