package io.casehub.clinical.cbr;

import io.casehub.clinical.api.AeEscalationCompletedEvent;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Observes {@link AeEscalationCompletedEvent} and stores a {@link FeatureVectorCbrCase}
 * representing the completed adverse event escalation.
 * <p>
 * Feature vector includes:
 * <ul>
 *   <li>grade — CTCAE grade (1-5)</li>
 *   <li>eventType — medical event type (e.g., "Neutropenia")</li>
 *   <li>trialPhase — trial phase (PHASE_I, PHASE_II, etc.)</li>
 *   <li>unexpected — whether the event was unexpected per investigator assessment</li>
 *   <li>suspected — whether the event is suspected to be related to the intervention</li>
 *   <li>safetyReviewOutcome — senior monitor recommendation (e.g., "CONTINUE_MONITORING")</li>
 *   <li>dsmbEscalated — whether the event triggered DSMB escalation</li>
 *   <li>indReportFiled — whether an IND expedited safety report was filed</li>
 *   <li>susarOversight — whether SUSAR oversight gate was completed</li>
 * </ul>
 * <p>
 * Cases are stored idempotently keyed by aeId — subsequent escalation completions
 * for the same AE replace the previous precedent.
 */
@ApplicationScoped
public class AeResolutionCbrWriter {

    private static final Logger LOG = Logger.getLogger(AeResolutionCbrWriter.class);

    @Inject
    ClinicalCbrService cbrService;

    /**
     * Consumes {@link AeEscalationCompletedEvent} and stores a feature vector CBR case.
     * <p>
     * Loads the AdverseEvent entity, traverses to TrialSite and ClinicalTrial to
     * extract trial phase, constructs a feature map, and stores the case via
     * {@link ClinicalCbrService#storeIdempotent}.
     *
     * @param event escalation completion event with aeId, grade, siteId, and outcomes
     */
    @Transactional
    public void onAeEscalationCompleted(@ObservesAsync AeEscalationCompletedEvent event) {
        try {
            AdverseEvent ae = AdverseEvent.findById(event.aeId());
            if (ae == null) {
                LOG.warnf("AE not found: %s", event.aeId());
                return;
            }

            TrialSite site = TrialSite.findById(event.siteId());
            ClinicalTrial trial = site != null ? ClinicalTrial.findById(site.trialId) : null;
            String trialPhase = trial != null ? trial.phase.name() : "UNKNOWN";

            Map<String, Object> features = Map.of(
                "grade", event.grade().ordinal() + 1,
                "eventType", ae.eventType != null ? ae.eventType : "UNKNOWN",
                "trialPhase", trialPhase,
                "unexpected", event.unexpected(),
                "suspected", ae.suspected,
                "safetyReviewOutcome", event.safetyReviewOutcome() != null ? event.safetyReviewOutcome() : "UNKNOWN",
                "dsmbEscalated", event.dsmbEscalated(),
                "indReportFiled", ae.regulatorySubmissionStatus != RegulatorySubmissionStatus.NONE,
                "susarOversight", ae.susarOversightStatus != SusarOversightStatus.NONE
            );

            String problem = "Grade %d %s in %s trial, unexpected=%s, suspected=%s".formatted(
                event.grade().ordinal() + 1, ae.eventType != null ? ae.eventType : "UNKNOWN", trialPhase, event.unexpected(), ae.suspected);
            String solution = "Safety review: %s. DSMB escalated: %s. IND report: %s. SUSAR oversight: %s.".formatted(
                event.safetyReviewOutcome() != null ? event.safetyReviewOutcome() : "UNKNOWN",
                event.dsmbEscalated(),
                ae.regulatorySubmissionStatus != RegulatorySubmissionStatus.NONE,
                ae.susarOversightStatus != SusarOversightStatus.NONE
            );

            var cbrCase = new FeatureVectorCbrCase(problem, solution, "COMPLETED", 1.0, features);
            cbrService.storeIdempotent(
                cbrCase,
                "clinical-ae",
                event.aeId().toString(),
                ClinicalCbrDomains.AE,
                ae.tenantId,
                ae.engineCaseId != null ? ae.engineCaseId.toString() : null
            );

            LOG.infof("Stored CBR case for AE %s: grade=%d, eventType=%s, phase=%s",
                event.aeId(), event.grade().ordinal() + 1, ae.eventType, trialPhase);

        } catch (Exception e) {
            LOG.errorf(e, "CBR store failed for AE %s", event.aeId());
        }
    }
}
