package io.casehub.clinical.cbr;

import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.api.spi.CaseOutcomeObserver;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.engine.common.internal.model.PlanItemRecord;
import io.casehub.engine.common.spi.PlanItemStore;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ClinicalCaseOutcomeObserver implements CaseOutcomeObserver {

    private static final Logger LOG = Logger.getLogger(ClinicalCaseOutcomeObserver.class);

    private static final Map<String, String> BINDING_CAPABILITY_MAP = Map.of(
        "safety-review", "safety-monitoring",
        "dsmb-escalation", "data-safety-monitoring"
    );

    private final ClinicalCbrService cbrService;
    private final CbrCaseMemoryStore store;
    private final PlanItemStore planItemStore;
    private final ClinicalScopeResolver scopeResolver;
    private EntityResolver entityResolver;
    private AeTrajectoryBuilder trajectoryBuilder;


    @Inject
    public ClinicalCaseOutcomeObserver(ClinicalCbrService cbrService,
                                       CbrCaseMemoryStore store,
                                       PlanItemStore planItemStore,
                                       AeTrajectoryBuilder trajectoryBuilder,
                                       ClinicalScopeResolver scopeResolver,
                                       io.casehub.ledger.runtime.repository.ActorTrustScoreRepository trustScoreRepository) {
        this.cbrService        = cbrService;
        this.store             = store;
        this.planItemStore     = planItemStore;
        this.trajectoryBuilder = trajectoryBuilder;
        this.scopeResolver     = scopeResolver;
        this.entityResolver    = new PanacheEntityResolver(trustScoreRepository);
    }

    void setEntityResolver(EntityResolver resolver) {
        this.entityResolver = resolver;
    }

    @Override
    @Transactional
    public void onOutcome(CaseOutcomeEvent event) {
        try {
            String entityId = resolveEntityId(event);
            if (entityId == null) {
                LOG.debugf("No recognized clinical entity in snapshot for case %s — skipping", event.caseId());
                return;
            }

            if (isAeCase(event)) {
                handleAeCase(event, entityId);
            }

            recordOutcome(entityId, event);
        } catch (Exception e) {
            LOG.errorf(e, "ClinicalCaseOutcomeObserver failed for case %s", event.caseId());
        }
    }

    private boolean isAeCase(CaseOutcomeEvent event) {
        return event.caseFileSnapshot().containsKey("aeId");
    }

    private String resolveEntityId(CaseOutcomeEvent event) {
        Map<String, Object> snapshot = event.caseFileSnapshot();
        if (snapshot.containsKey("aeId")) return String.valueOf(snapshot.get("aeId"));
        if (snapshot.containsKey("deviationId")) return String.valueOf(snapshot.get("deviationId"));
        if (snapshot.containsKey("amendmentId")) return String.valueOf(snapshot.get("amendmentId"));
        return null;
    }

    private void handleAeCase(CaseOutcomeEvent event, String aeIdStr) {
        UUID aeId;
        try {
            aeId = UUID.fromString(aeIdStr);
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid aeId format in snapshot: %s", aeIdStr);
            return;
        }

        AdverseEvent ae = entityResolver.findAe(aeId);
        if (ae == null) {
            LOG.warnf("AE not found: %s — skipping CBR storage", aeId);
            return;
        }

        java.util.Optional<io.casehub.platform.api.path.Path> scopeOpt = scopeResolver.forAdverseEvent(ae);
        if (scopeOpt.isEmpty()) {
            LOG.warnf("Cannot resolve scope for AE %s — skipping CBR storage", aeId);
            return;
        }
        io.casehub.platform.api.path.Path scope = scopeOpt.get();

        PatientEnrollment enrollment = ae.enrollmentId != null
                                       ? entityResolver.findEnrollment(ae.enrollmentId) : null;
        TrialSite site = enrollment != null && enrollment.siteId != null
                         ? entityResolver.findSite(enrollment.siteId) : null;
        ClinicalTrial trial = site != null && site.trialId != null
                              ? entityResolver.findTrial(site.trialId) : null;

        long priorAeCount = ae.enrollmentId != null
                            ? entityResolver.countPriorAes(ae.enrollmentId, aeId) : 0;
        long siteEnrollmentCount = site != null
                                   ? entityResolver.countEnrollmentsAtSite(site.id) : 0;
        int siteTargetEnrollment = site != null ? site.targetEnrollment : 0;

        Map<String, Object> snapshot = event.caseFileSnapshot();
        String safetyReviewOutcome = snapshot.get("safetyReview") != null
                                     ? String.valueOf(snapshot.get("safetyReview")) : null;
        boolean dsmbEscalated = "true".equals(String.valueOf(snapshot.get("dsmbEscalation")));

        List<PlanTrace> planTraces = buildPlanTraces(event.caseId(), event.tenancyId());

        String agentId = planTraces.stream()
                                   .filter(pt -> "safety-monitoring".equals(pt.capabilityName()))
                                   .map(PlanTrace::workerName)
                                   .filter(java.util.Objects::nonNull)
                                   .findFirst()
                                   .orElse(null);
        double agentTrustScore = agentId != null
                                 ? entityResolver.findAgentTrustScore(agentId) : 0.5;

        var ctx = new AeCbrContext(ae, enrollment, trial, safetyReviewOutcome,
                                   dsmbEscalated, priorAeCount, siteEnrollmentCount, siteTargetEnrollment, agentTrustScore);

        Map<String, Object> features = AeCbrFeatureBuilder.buildFeatures(ctx);
        String              problem  = AeCbrFeatureBuilder.buildProblemSummary(ctx);
        String              solution = AeCbrFeatureBuilder.buildSolutionSummary(ctx);

        var cbrCase = new PlanCbrCase(problem, solution, event.outcomeLabel(), Confidence.unknown(1.0), FeatureValue.toFeatureMap(features), planTraces, null, null);

        cbrService.storeIdempotent(
                cbrCase, "clinical-ae", aeId.toString(),
                ClinicalCbrDomains.AE, ae.tenantId,
                event.caseId() != null ? event.caseId().toString() : null,
                scope);

        LOG.infof("Stored CBR case for AE %s: grade=%s, eventType=%s, planTraces=%d, agentTrust=%.2f",
                  aeId, ae.grade, ae.eventType, planTraces.size(), agentTrustScore);

        try {
            List<Map<String, FeatureValue>> trajectory = trajectoryBuilder.buildTrajectory(ae, ae.tenantId);
            if (!trajectory.isEmpty()) {
                Map<String, Object> trajFeatures = new java.util.LinkedHashMap<>(features);
                trajFeatures.put("aeTrajectory", trajectory);
                var trajCbrCase = new PlanCbrCase(problem, solution, event.outcomeLabel(), Confidence.unknown(1.0), FeatureValue.toFeatureMap(trajFeatures), planTraces, null, null);
                cbrService.storeIdempotent(
                        trajCbrCase, "clinical-ae-trajectory", aeId + "-trajectory",
                        ClinicalCbrDomains.AE_TRAJECTORY, ae.tenantId,
                        event.caseId() != null ? event.caseId().toString() : null,
                        scope);
                LOG.infof("Stored trajectory CBR case for AE %s: observations=%d", aeId, trajectory.size());
            }
        } catch (Exception e) {
            LOG.warnf(e, "Trajectory CBR case storage failed for AE %s — point-in-time case was stored successfully", aeId);
        }}

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
                                0,
                                Map.of(),
                                null))
                        .toList();}

    private void recordOutcome(String entityId, CaseOutcomeEvent event) {
        double successRate = switch (event.outcomeLabel()) {
            case "COMPLETED" -> 1.0;
            case "FAULTED", "CANCELLED" -> 0.0;
            default -> 0.5;
        };

        CbrOutcome outcome = CbrOutcome.of(successRate, event.outcomeLabel(), event.closedAt());
        store.recordOutcome(entityId, event.tenancyId(), outcome);
    }

    interface EntityResolver {
        AdverseEvent findAe(UUID aeId);
        PatientEnrollment findEnrollment(UUID enrollmentId);
        TrialSite findSite(UUID siteId);
        ClinicalTrial findTrial(UUID trialId);
        long countPriorAes(UUID enrollmentId, UUID excludeAeId);
        long countEnrollmentsAtSite(UUID siteId);
        double findAgentTrustScore(String actorId);
    }

    private static class PanacheEntityResolver implements EntityResolver {
        private final io.casehub.ledger.runtime.repository.ActorTrustScoreRepository trustScoreRepository;

        PanacheEntityResolver(io.casehub.ledger.runtime.repository.ActorTrustScoreRepository trustScoreRepository) {
            this.trustScoreRepository = trustScoreRepository;
        }

        @Override public AdverseEvent findAe(UUID aeId) { return AdverseEvent.findById(aeId); }
        @Override public PatientEnrollment findEnrollment(UUID id) { return PatientEnrollment.findById(id); }
        @Override public TrialSite findSite(UUID id) { return TrialSite.findById(id); }
        @Override public ClinicalTrial findTrial(UUID id) { return ClinicalTrial.findById(id); }
        @Override public long countPriorAes(UUID enrollmentId, UUID excludeAeId) {
            return AdverseEvent.count("enrollmentId = ?1 and id != ?2", enrollmentId, excludeAeId);
        }
        @Override public long countEnrollmentsAtSite(UUID siteId) {
            return PatientEnrollment.count("siteId", siteId);
        }
        @Override public double findAgentTrustScore(String actorId) {
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
}
