package io.casehub.clinical.service;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.clinical.api.AeEscalationCompletedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.UUID;

/**
 * Clears a site's Grade 4+ active flag in the trial blackboard when its AE
 * escalation case completes. This allows the DSMB rollup binding to re-evaluate
 * when the cross-site safety pattern resolves.
 *
 * <p>Layer 6 simplification: uses a boolean flag per site. If a site has multiple
 * concurrent Grade 4+ AEs, the first completion clears the flag. A production
 * implementation would recompute from domain truth (query unresolved AEs).
 */
@ApplicationScoped
public class TrialSafetySignalService {

    private static final Set<CtcaeGrade> SEVERE_GRADES = Set.of(CtcaeGrade.GRADE_4, CtcaeGrade.GRADE_5);

    @Inject CaseHubRuntime runtime;
    @Inject TrialCaseLookup trialCaseLookup;

    /** Sets the grade4Active flag when a Grade 4/5 AE escalation case starts. */
    public void signalGrade4Active(UUID siteId) {
        UUID trialCaseId = trialCaseLookup.findTrialEngineCase(siteId);
        if (trialCaseId == null) return;
        runtime.signal(trialCaseId, "grade4Active." + siteId, Boolean.TRUE);
    }

    public void onAeEscalationCompleted(@ObservesAsync AeEscalationCompletedEvent event) {
        if (!SEVERE_GRADES.contains(event.grade())) return;
        if (event.siteId() == null) return;

        UUID trialCaseId = trialCaseLookup.findTrialEngineCase(event.siteId());
        if (trialCaseId == null) return;

        runtime.signal(trialCaseId, "grade4Active." + event.siteId(), Boolean.FALSE);
    }
}
