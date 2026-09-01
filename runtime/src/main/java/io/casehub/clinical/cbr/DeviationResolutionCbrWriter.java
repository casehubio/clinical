package io.casehub.clinical.cbr;

import io.casehub.clinical.api.IrbApprovalResolvedEvent;
import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.IrbDecision;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.IrbApproval;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.*;

/**
 * Observes {@link ProtocolDeviationResolvedEvent} and {@link IrbApprovalResolvedEvent}
 * to store {@link PlanCbrCase} precedents for protocol deviation resolution patterns.
 * <p>
 * Feature vector includes:
 * <ul>
 *   <li>deviationType — categorical (e.g., "CONSENT_TIMING_DELAY")</li>
 *   <li>severity — MINOR, MAJOR, CRITICAL</li>
 *   <li>escalationRequirement — NONE, SPONSOR_NOTIFICATION, IRB_REVIEW</li>
 *   <li>piDecision — APPROVED, REJECTED, EXPIRED, ESCALATED</li>
 *   <li>irbDecision — APPROVED, REJECTED, DEFERRED, EXPIRED, or N/A</li>
 * </ul>
 * <p>
 * Plan trace records the binding execution sequence:
 * <ul>
 *   <li>pi-oversight (capability: pi-authorisation) — PI COMMAND/RESPONSE outcome</li>
 *   <li>irb-committee (capability: irb-consultation) — IRB WorkItem decision (CRITICAL only)</li>
 * </ul>
 * <p>
 * Two observers share a private {@code buildAndStore()} method that loads current
 * entity state and writes the case. Erase-before-store semantics ensure the IRB
 * observer overwrites the PI observer's incomplete case (CRITICAL deviations only).
 */
@ApplicationScoped
public class DeviationResolutionCbrWriter {

    private static final Logger LOG = Logger.getLogger(DeviationResolutionCbrWriter.class);

    @Inject
    ClinicalCbrService cbrService;

    @Inject
    ClinicalScopeResolver scopeResolver;

    /**
     * Consumes {@link ProtocolDeviationResolvedEvent} and stores a plan CBR case.
     * <p>
     * For MINOR/MAJOR deviations, this is the final state (no IRB).
     * For CRITICAL deviations, the IRB decision may still be PENDING — this
     * stores an incomplete case that will be overwritten when IRB decides.
     *
     * @param event PI authorisation terminal state event
     */
    @Transactional
    public void onProtocolDeviationResolved(@ObservesAsync ProtocolDeviationResolvedEvent event) {
        try {
            buildAndStore(event.deviationId(), event.tenantId());
        } catch (Exception e) {
            LOG.errorf(e, "CBR store failed for deviation %s", event.deviationId());
        }
    }

    /**
     * Consumes {@link IrbApprovalResolvedEvent} and re-stores the plan CBR case
     * with the complete IRB decision.
     * <p>
     * Overwrites the case stored by {@link #onProtocolDeviationResolved} (which
     * had irbDecision = N/A). This gives the full CRITICAL deviation outcome.
     *
     * @param event IRB committee decision event
     */
    @Transactional
    public void onIrbApprovalResolved(@ObservesAsync IrbApprovalResolvedEvent event) {
        try {
            buildAndStore(event.deviationId(), event.tenantId());
        } catch (Exception e) {
            LOG.errorf(e, "CBR store failed for deviation %s (IRB)", event.deviationId());
        }
    }

    /**
     * Shared logic: load deviation + IRB approval (if any), build PlanCbrCase, store.
     * <p>
     * Called by both event observers. The second call (IRB) overwrites the first (PI)
     * via erase-before-store.
     */
    private void buildAndStore(UUID deviationId, String tenantId) {
        ProtocolDeviation deviation = ProtocolDeviation.findById(deviationId);
        if (deviation == null) {
            LOG.warnf("Deviation not found: %s", deviationId);
            return;
        }

        java.util.Optional<io.casehub.platform.api.path.Path> scopeOpt = scopeResolver.forDeviation(deviation);
        if (scopeOpt.isEmpty()) {
            LOG.warnf("Cannot resolve scope for deviation %s — skipping CBR storage", deviationId);
            return;
        }
        io.casehub.platform.api.path.Path scope = scopeOpt.get();

        IrbApproval irbApproval = IrbApproval.find("deviationId", deviationId).firstResult();

        // Feature vector
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("deviationType", deviation.deviationType);
        features.put("severity", deviation.severity.name());
        features.put("escalationRequirement", deviation.escalationRequirement.name());
        features.put("piDecision", deviation.piApprovalStatus.name());
        features.put("irbDecision", irbApproval != null && irbApproval.decision != IrbDecision.PENDING
            ? irbApproval.decision.name()
            : "N/A");

        // Plan trace — reconstruct from entity state
        List<PlanTrace> trace = new ArrayList<>();

        // PI oversight binding (always present)
        trace.add(new PlanTrace(
            "pi-oversight",
            "pi-authorisation",
            null,
            deviation.piApprovalStatus.name(),
            1,
            Map.of(),
            null
        ));

        // IRB committee binding (CRITICAL only, after IRB decides)
        if (irbApproval != null && irbApproval.decision != IrbDecision.PENDING) {
            trace.add(new PlanTrace(
                "irb-committee",
                "irb-consultation",
                null,
                irbApproval.decision.name(),
                2,
                Map.of(),
                null
            ));
        }

        // Problem and solution summaries
        String problem = String.format(
            "%s deviation (severity: %s, escalation: %s)",
            deviation.deviationType,
            deviation.severity.name(),
            deviation.escalationRequirement.name()
        );

        String solution = String.format(
            "PI decision: %s%s",
            deviation.piApprovalStatus.name(),
            irbApproval != null && irbApproval.decision != IrbDecision.PENDING
                ? ". IRB decision: " + irbApproval.decision.name()
                : ""
        );

        PlanCbrCase cbrCase = new PlanCbrCase(problem, solution, "RESOLVED", Confidence.unknown(1.0), FeatureValue.toFeatureMap(features), trace, null, null);

        cbrService.storeIdempotent(
            cbrCase,
            "clinical-deviation",
            deviationId.toString(),
            ClinicalCbrDomains.DEVIATION,
            deviation.tenantId,
            deviation.engineCaseId != null ? deviation.engineCaseId.toString() : null,
            scope
        );

        LOG.infof("Stored CBR case for deviation %s: type=%s, severity=%s, piDecision=%s, irbDecision=%s",
            deviationId,
            deviation.deviationType,
            deviation.severity.name(),
            deviation.piApprovalStatus.name(),
            features.get("irbDecision"));
    }
}
