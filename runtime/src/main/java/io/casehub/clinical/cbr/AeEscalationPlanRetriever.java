package io.casehub.clinical.cbr;

import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.neocortex.memory.cbr.*;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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
    private final EntityManager em;
    private EntityResolver entityResolver;

    @ConfigProperty(name = "casehub.clinical.cbr.escalation-plan.top-k", defaultValue = "5")
    int topK;

    @ConfigProperty(name = "casehub.clinical.cbr.escalation-plan.min-similarity", defaultValue = "0.4")
    double minSimilarity;

    @Inject
    public AeEscalationPlanRetriever(ClinicalCbrService cbrService, PlanAdapter planAdapter,
                                      ClinicalScopeResolver scopeResolver, ClinicalCbrConfig cbrConfig,
                                      EntityManager em) {
        this.cbrService = cbrService;
        this.planAdapter = planAdapter;
        this.scopeResolver = scopeResolver;
        this.cbrConfig = cbrConfig;
        this.em = em;
        this.entityResolver = new JpaEntityResolver();
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

            long siteEnrollmentCount = site != null ? entityResolver.countEnrollmentsAtSite(site.id) : 0;
            int siteTargetEnrollment = site != null ? site.targetEnrollment : 0;
            var ctx = new AeCbrContext(ae, enrollment, trial, null, false, priorAeCount, siteEnrollmentCount, siteTargetEnrollment, 0.5);
            Map<String, Object> rawFeatures = AeCbrFeatureBuilder.buildQueryFeatures(ctx);
            Map<String, FeatureValue> featureMap = FeatureValue.toFeatureMap(rawFeatures);

            Path scope = scopeResolver.forAdverseEvent(ae).orElse(Path.root());
            CbrQuery query = CbrQuery.of(ae.tenantId, ClinicalCbrDomains.AE, scope,
                            "clinical-ae", featureMap, topK)
                    .withMinSimilarity(minSimilarity)
                    .withScopeDecay(cbrConfig.aeScopeDecay())
                    .withTemporalDecay(cbrConfig.aeTemporalDecay());

            AuditedRetrievalResult<ResolvedCase> result = cbrService.retrieveWithAudit(
                    query, ResolvedCase.class, ae.id, "system:ae-escalation");

            if (result.cases().isEmpty()) {
                return EscalationPlanRecommendation.none();
            }

            ScoredCbrCase<ResolvedCase> topCase = result.cases().get(0);
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
        long countEnrollmentsAtSite(UUID siteId);
    }

    private class JpaEntityResolver implements EntityResolver {
        @Override
        public PatientEnrollment findEnrollment(UUID id) { return em.find(PatientEnrollment.class, id); }
        @Override
        public TrialSite findSite(UUID id) { return em.find(TrialSite.class, id); }
        @Override
        public ClinicalTrial findTrial(UUID id) { return em.find(ClinicalTrial.class, id); }
        @Override
        public long countPriorAes(UUID enrollmentId, UUID excludeAeId) {
            return em.createQuery("SELECT COUNT(a) FROM AdverseEvent a WHERE a.enrollmentId = :enrollmentId AND a.id != :excludeAeId", Long.class)
                    .setParameter("enrollmentId", enrollmentId).setParameter("excludeAeId", excludeAeId).getSingleResult();
        }
        @Override
        public long countEnrollmentsAtSite(UUID siteId) {
            return em.createQuery("SELECT COUNT(e) FROM PatientEnrollment e WHERE e.siteId = :siteId", Long.class)
                    .setParameter("siteId", siteId).getSingleResult();
        }
    }
}
