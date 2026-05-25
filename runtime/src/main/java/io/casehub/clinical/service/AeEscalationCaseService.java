package io.casehub.clinical.service;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.spi.AdverseEventContext;
import io.casehub.clinical.api.spi.AdverseEventEscalationPolicy;
import io.casehub.clinical.api.spi.AdverseEventEscalationRequirements;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Observes AdverseEventReportedEvent (Grade 3+ AEs) and starts an AE escalation
 * engine case. Re-evaluates AdverseEventEscalationPolicy to populate initial context.
 *
 * <p>Policy is called twice — once in AdverseEventService (routing decision),
 * once here (case context keys). This is intentional: each consumer calls the
 * policy for its own concern. The SPI must be idempotent.
 *
 * <p>For Grade 4+ AEs, also signals the trial-level case blackboard so the
 * trial's DSMB rollup binding can detect cross-site safety patterns.
 */
@ApplicationScoped
public class AeEscalationCaseService {

    private static final Set<CtcaeGrade> SEVERE_GRADES = Set.of(CtcaeGrade.GRADE_4, CtcaeGrade.GRADE_5);

    @Inject ClinicalAdverseEventCaseHub caseHub;
    @Inject AdverseEventEscalationPolicy policy;
    @Inject CaseHubRuntime runtime;
    @Inject TrialCaseLookup trialCaseLookup;

    @Transactional
    public void onAdverseEventReported(@ObservesAsync AdverseEventReportedEvent event) {
        AdverseEventEscalationRequirements requirements = policy.evaluate(
                new AdverseEventContext(event.aeId(), event.enrollmentId(), event.siteId(), event.grade()));

        Map<String, Object> initialContext = new HashMap<>();
        initialContext.put("aeId", event.aeId().toString());
        initialContext.put("enrollmentId", event.enrollmentId().toString());
        initialContext.put("siteId", event.siteId().toString());
        initialContext.put("grade", event.grade().name());
        initialContext.put("requiresSeniorMonitor", requirements.requiresSeniorMonitor());
        initialContext.put("requiresDsmbEscalation", requirements.requiresDsmbEscalation());

        caseHub.startCase(initialContext);

        if (SEVERE_GRADES.contains(event.grade())) {
            signalTrialGrade4Active(event.siteId(), true);
        }
    }

    private void signalTrialGrade4Active(UUID siteId, boolean active) {
        UUID trialCaseId = trialCaseLookup.findTrialEngineCase(siteId);
        if (trialCaseId != null) {
            runtime.signal(trialCaseId, "grade4Active." + siteId, active);
        }
    }
}
