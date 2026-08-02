package io.casehub.clinical.cbr;

import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.neocortex.memory.cbr.*;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AeEscalationPlanRetriever {

    private static final Logger LOG = Logger.getLogger(AeEscalationPlanRetriever.class);

    private final ClinicalCbrService cbrService;
    private final PlanAdapter planAdapter;
    private final ClinicalScopeResolver scopeResolver;
    private final ClinicalCbrConfig cbrConfig;
    private EntityResolver entityResolver;

    @ConfigProperty(name = "casehub.clinical.cbr.escalation-plan.top-k", defaultValue = "5")
    int topK;

    @ConfigProperty(name = "casehub.clinical.cbr.escalation-plan.min-similarity", defaultValue = "0.4")
    double minSimilarity;

    @Inject
    public AeEscalationPlanRetriever(ClinicalCbrService cbrService, PlanAdapter planAdapter,
                                      ClinicalScopeResolver scopeResolver, ClinicalCbrConfig cbrConfig) {
        this.cbrService = cbrService;
        this.planAdapter = planAdapter;
        this.scopeResolver = scopeResolver;
        this.cbrConfig = cbrConfig;
        this.entityResolver = new PanacheEntityResolver();
    }

    void setEntityResolver(EntityResolver resolver) {
        this.entityResolver = resolver;
    }

    public EscalationPlanRecommendation retrieve(AdverseEvent ae) {
        try {
            PatientEnrollment enrollment = ae.enrollmentId != null
                    ? entityResolver.findEnrollment(ae.enrollmentId) : null;
            TrialSite site = enrollment != null && enrollment.siteId != null
                    ? entityResolver.findSite(enrollment.siteId) : null;
            ClinicalTrial trial = site != null && site.trialId != null
                    ? entityResolver.findTrial(site.trialId) : null;
            long priorAeCount = ae.enrollmentId != null
                    ? entityResolver.countPriorAes(ae.enrollmentId, ae.id) : 0;

            Map<String, Object> rawFeatures = AeCbrFeatureBuilder.buildQueryFeatures(
                    ae, enrollment, trial, priorAeCount);
            Map<String, FeatureValue> featureMap = FeatureValue.toFeatureMap(rawFeatures);

            Path scope = scopeResolver.forAdverseEvent(ae).orElse(Path.root());
            CbrQuery query = CbrQuery.of(ae.tenantId, ClinicalCbrDomains.AE, scope,
                            "clinical-ae", featureMap, topK)
                    .withMinSimilarity(minSimilarity)
                    .withScopeDecay(cbrConfig.aeScopeDecay())
                    .withTemporalDecay(cbrConfig.aeTemporalDecay());

            AuditedRetrievalResult<PlanCbrCase> result = cbrService.retrieveWithAudit(
                    query, PlanCbrCase.class, ae.id, "system:ae-escalation");

            if (result.cases().isEmpty()) {
                return EscalationPlanRecommendation.none();
            }

            ScoredCbrCase<PlanCbrCase> topCase = result.cases().get(0);
            AdaptedPlan adapted = planAdapter.adapt("clinical-ae", topCase, featureMap);

            return new EscalationPlanRecommendation(
                    adapted, result.cases().size(), topCase.score(),
                    result.traceId(), result.explanation());
        } catch (Exception e) {
            LOG.warnf(e, "Escalation plan retrieval failed for AE %s — proceeding without recommendation", ae.id);
            return EscalationPlanRecommendation.none();
        }
    }

    interface EntityResolver {
        PatientEnrollment findEnrollment(UUID id);
        TrialSite findSite(UUID id);
        ClinicalTrial findTrial(UUID id);
        long countPriorAes(UUID enrollmentId, UUID excludeAeId);
    }

    private static class PanacheEntityResolver implements EntityResolver {
        @Override
        public PatientEnrollment findEnrollment(UUID id) { return PatientEnrollment.findById(id); }
        @Override
        public TrialSite findSite(UUID id) { return TrialSite.findById(id); }
        @Override
        public ClinicalTrial findTrial(UUID id) { return ClinicalTrial.findById(id); }
        @Override
        public long countPriorAes(UUID enrollmentId, UUID excludeAeId) {
            return AdverseEvent.count("enrollmentId = ?1 and id != ?2", enrollmentId, excludeAeId);
        }
    }
}
