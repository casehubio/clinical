package io.casehub.clinical.cbr;

import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.engine.common.internal.model.PlanItemRecord;
import io.casehub.engine.common.spi.PlanItemStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AeCbrCaseBuilder {

    private static final Logger LOG = Logger.getLogger(AeCbrCaseBuilder.class);

    private static final Map<String, String> BINDING_CAPABILITY_MAP = Map.of(
        "safety-review", "safety-monitoring",
        "dsmb-escalation", "data-safety-monitoring"
    );

    private final ClinicalCbrService cbrService;
    private final ClinicalScopeResolver scopeResolver;
    private final PlanItemStore planItemStore;
    private final AeTrajectoryBuilder trajectoryBuilder;
    private final io.casehub.ledger.runtime.repository.ActorTrustScoreRepository trustScoreRepository;

    @Inject
    public AeCbrCaseBuilder(ClinicalCbrService cbrService,
                            ClinicalScopeResolver scopeResolver,
                            PlanItemStore planItemStore,
                            AeTrajectoryBuilder trajectoryBuilder,
                            io.casehub.ledger.runtime.repository.ActorTrustScoreRepository trustScoreRepository) {
        this.cbrService = cbrService;
        this.scopeResolver = scopeResolver;
        this.planItemStore = planItemStore;
        this.trajectoryBuilder = trajectoryBuilder;
        this.trustScoreRepository = trustScoreRepository;
    }

    public void buildAndStore(AdverseEvent ae,
                              PatientEnrollment enrollment,
                              TrialSite site,
                              ClinicalTrial trial,
                              String safetyReviewOutcome,
                              boolean dsmbEscalated,
                              String regradeSource,
                              UUID engineCaseId,
                              String tenantId) {
        Optional<Path> scopeOpt = scopeResolver.forAdverseEvent(ae);
        if (scopeOpt.isEmpty()) {
            LOG.warnf("Cannot resolve scope for AE %s — skipping CBR store", ae.id);
            return;
        }
        Path scope = scopeOpt.get();

        long priorAeCount = ae.enrollmentId != null
            ? countPriorAes(ae.enrollmentId, ae.id) : 0;
        long siteEnrollmentCount = site != null
            ? countEnrollmentsAtSite(site.id) : 0;
        int siteTargetEnrollment = site != null ? site.targetEnrollment : 0;

        List<PlanTrace> planTraces = engineCaseId != null
            ? buildPlanTraces(engineCaseId, tenantId) : List.of();

        String agentId = planTraces.stream()
            .filter(pt -> "safety-monitoring".equals(pt.capabilityName()))
            .map(PlanTrace::workerName)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
        double agentTrustScore = agentId != null ? findAgentTrustScore(agentId) : 0.5;

        var ctx = new AeCbrContext(ae, enrollment, trial, safetyReviewOutcome,
            dsmbEscalated, priorAeCount, siteEnrollmentCount, siteTargetEnrollment, agentTrustScore);

        Map<String, Object> features = AeCbrFeatureBuilder.buildFeatures(ctx);
        if (regradeSource != null) {
            features.put("regradeSource", regradeSource);
        }
        String problem = AeCbrFeatureBuilder.buildProblemSummary(ctx);
        String solution = AeCbrFeatureBuilder.buildSolutionSummary(ctx);

        var cbrCase = new PlanCbrCase(
            problem, solution, "COMPLETED", 1.0,
            FeatureValue.toFeatureMap(features), planTraces,
            null, null);

        cbrService.storeIdempotent(
            cbrCase, "clinical-ae", ae.id.toString(),
            ClinicalCbrDomains.AE, tenantId,
            engineCaseId != null ? engineCaseId.toString() : null,
            scope);

        LOG.infof("Stored CBR case for AE %s: grade=%s, regradeSource=%s",
            ae.id, ae.grade, regradeSource);

        try {
            List<Map<String, FeatureValue>> trajectory = trajectoryBuilder.buildTrajectory(ae, tenantId);
            if (!trajectory.isEmpty()) {
                Map<String, Object> trajFeatures = new java.util.LinkedHashMap<>(features);
                trajFeatures.put("aeTrajectory", trajectory);
                var trajCbrCase = new PlanCbrCase(
                    problem, solution, "COMPLETED", 1.0,
                    FeatureValue.toFeatureMap(trajFeatures), planTraces,
                    null, null);
                cbrService.storeIdempotent(
                    trajCbrCase, "clinical-ae-trajectory", ae.id + "-trajectory",
                    ClinicalCbrDomains.AE_TRAJECTORY, tenantId,
                    engineCaseId != null ? engineCaseId.toString() : null,
                    scope);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Trajectory CBR case storage failed for AE %s", ae.id);
        }
    }

    long countPriorAes(UUID enrollmentId, UUID excludeAeId) {
        return AdverseEvent.count("enrollmentId = ?1 and id != ?2", enrollmentId, excludeAeId);
    }

    long countEnrollmentsAtSite(UUID siteId) {
        return PatientEnrollment.count("siteId", siteId);
    }

    private List<PlanTrace> buildPlanTraces(UUID caseId, String tenancyId) {
        List<PlanItemRecord> planItems = planItemStore.findByCaseId(caseId, tenancyId);
        return planItems.stream()
            .filter(pi -> pi.status().isTerminal())
            .filter(pi -> pi.executorName() != null)
            .filter(pi -> BINDING_CAPABILITY_MAP.containsKey(pi.bindingName()))
            .map(pi -> new PlanTrace(
                pi.bindingName(),
                BINDING_CAPABILITY_MAP.get(pi.bindingName()),
                pi.executorName(),
                pi.status().name(),
                0, Map.of(), null))
            .toList();
    }

    private double findAgentTrustScore(String actorId) {
        try {
            return trustScoreRepository
                .findCapabilityDimension(actorId, "safety-monitoring",
                    io.casehub.clinical.api.ClinicalTrustDimensions.SAFETY_ACCURACY)
                .map(s -> s.trustScore)
                .orElse(0.5);
        } catch (Exception e) {
            return 0.5;
        }
    }
}
