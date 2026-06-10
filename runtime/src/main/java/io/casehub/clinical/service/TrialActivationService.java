package io.casehub.clinical.service;

import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Transitions a trial to ACTIVE and starts its coordination engine case.
 *
 * <p>Three-phase activation — each phase is in its own transaction so that
 * {@code startCase().join()} never holds a DB connection while blocked on the
 * engine's async persistence. Holding a connection across {@code join()} would
 * deadlock under Agroal pool exhaustion when the engine also needs a connection.
 */
@ApplicationScoped
public class TrialActivationService {

    public static class TrialNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public TrialNotFoundException(UUID id) { super("Trial not found: " + id); }
    }

    public static class TrialNotInPlanningStatusException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public TrialNotInPlanningStatusException(TrialStatus status) { super("Trial status is " + status + ", expected PLANNING"); }
    }

    @Inject ClinicalTrialCaseHub caseHub;
    @Inject CurrentPrincipal principal;

    public void activate(UUID trialId) {
        Map<String, Object> initialContext = markActive(trialId);
        UUID caseId = caseHub.startCase(initialContext).toCompletableFuture().join();
        persistCaseId(trialId, caseId);
    }

    @Transactional
    Map<String, Object> markActive(UUID trialId) {
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null) throw new TrialNotFoundException(trialId);
        if (trial.status != TrialStatus.PLANNING) throw new TrialNotInPlanningStatusException(trial.status);
        trial.status = TrialStatus.ACTIVE;

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("trialId", trialId.toString());
        ctx.put("protocolId", trial.protocolId);
        ctx.put("grade4Active", new HashMap<>());
        return ctx;
    }

    @Transactional
    void persistCaseId(UUID trialId, UUID caseId) {
        ClinicalTrial trial = ClinicalTrial.findById(trialId);
        trial.engineCaseId = caseId;
    }
}
