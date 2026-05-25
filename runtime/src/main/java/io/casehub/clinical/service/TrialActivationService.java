package io.casehub.clinical.service;

import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.entity.ClinicalTrial;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;

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

    @Inject ClinicalTrialCaseHub caseHub;

    public void activate(UUID trialId) {
        Map<String, Object> initialContext = markActive(trialId);
        UUID caseId = caseHub.startCase(initialContext).toCompletableFuture().join();
        persistCaseId(trialId, caseId);
    }

    @Transactional
    Map<String, Object> markActive(UUID trialId) {
        ClinicalTrial trial = ClinicalTrial.findById(trialId);
        if (trial == null) {
            throw new ClientErrorException(Response.Status.NOT_FOUND);
        }
        if (trial.status != TrialStatus.PLANNING) {
            throw new ClientErrorException(Response.Status.CONFLICT);
        }
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
