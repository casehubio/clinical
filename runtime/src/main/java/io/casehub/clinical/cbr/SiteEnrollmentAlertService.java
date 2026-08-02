package io.casehub.clinical.cbr;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.api.SiteEnrollmentAlertEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class SiteEnrollmentAlertService {

    private static final Logger LOG = Logger.getLogger(SiteEnrollmentAlertService.class);

    private final SiteEnrollmentTrajectoryBuilder trajectoryBuilder;
    private final ClinicalCbrService cbrService;
    private final Event<SiteEnrollmentAlertEvent> alertEvents;
    private final ClinicalCbrConfig cbrConfig;
    private Function<UUID, ClinicalTrial> trialFinder = id -> ClinicalTrial.findById(id);
    private BiFunction<UUID, String, Instant> earliestEnrollmentFinder = SiteEnrollmentAlertService::defaultEarliestEnrollment;

    @ConfigProperty(name = "casehub.clinical.trajectory.alert.min-matches", defaultValue = "2")
    int minMatches;

    @ConfigProperty(name = "casehub.clinical.trajectory.alert.min-similarity", defaultValue = "0.5")
    double minSimilarity;

    @ConfigProperty(name = "casehub.clinical.trajectory.alert.min-probability", defaultValue = "0.6")
    double minProbability;

    @Inject
    public SiteEnrollmentAlertService(SiteEnrollmentTrajectoryBuilder trajectoryBuilder,
                                       ClinicalCbrService cbrService,
                                       Event<SiteEnrollmentAlertEvent> alertEvents,
                                       ClinicalCbrConfig cbrConfig) {
        this.trajectoryBuilder = trajectoryBuilder;
        this.cbrService = cbrService;
        this.alertEvents = alertEvents;
        this.cbrConfig = cbrConfig;
    }

    void setTrialFinder(Function<UUID, ClinicalTrial> finder) {
        this.trialFinder = finder;
    }

    void setEarliestEnrollmentFinder(BiFunction<UUID, String, Instant> finder) {
        this.earliestEnrollmentFinder = finder;
    }

    public Optional<SiteEnrollmentAlertEvent> evaluate(UUID siteId, UUID trialId, String tenantId) {
        try {
            ClinicalTrial trial = trialFinder.apply(trialId);
            if (trial == null) {
                LOG.debugf("SiteEnrollmentAlertService: trial not found for trialId=%s", trialId);
                return Optional.empty();
            }

            Instant trialActivatedAt = earliestEnrollmentFinder.apply(siteId, tenantId);
            if (trialActivatedAt == null) return Optional.empty();

            var trajectory = trajectoryBuilder.buildTrajectory(siteId, trialId, trialActivatedAt, tenantId);
            if (trajectory.isEmpty()) return Optional.empty();

            Map<String, FeatureValue> features = new LinkedHashMap<>();
            features.put("trialPhase", FeatureValue.string(trial.phase != null ? trial.phase.name() : "UNKNOWN"));
            features.put("enrollmentRate", FeatureValue.structList(trajectory));

            io.casehub.platform.api.path.Path scope = io.casehub.platform.api.path.Path.of(trialId.toString(), siteId.toString());
            CbrQuery query = CbrQuery.of(tenantId, ClinicalCbrDomains.SITE_ENROLLMENT, scope,
                            "clinical-site-enrollment", features, 10)
                    .withMinSimilarity(minSimilarity)
                    .withScopeDecay(cbrConfig.siteEnrollmentScopeDecay())
                    .withTemporalDecay(cbrConfig.siteEnrollmentTemporalDecay());

            AuditedRetrievalResult<PlanCbrCase> result = cbrService.retrieveWithAudit(
                    query, PlanCbrCase.class, siteId, ClinicalActors.CLINICAL_SERVICE);

            if (result.cases().size() < minMatches) return Optional.empty();

            var prediction = predictOutcome(result.cases());
            if (prediction.probability() < minProbability) return Optional.empty();

            var event = new SiteEnrollmentAlertEvent(
                    siteId, trialId,
                    result.cases().size(), result.cases().get(0).score(),
                    prediction.outcome(), prediction.probability(),
                    result.traceId(), tenantId);
            alertEvents.fireAsync(event);
            return Optional.of(event);
        } catch (Exception e) {
            LOG.warnf(e, "SiteEnrollmentAlertService: evaluation failed for siteId=%s", siteId);
            return Optional.empty();
        }
    }

    record Prediction(String outcome, double probability) {}

    private Prediction predictOutcome(List<ScoredCbrCase<PlanCbrCase>> cases) {
        Map<String, Double> scoresByOutcome = cases.stream()
                .filter(c -> c.cbrCase().outcome() != null)
                .collect(Collectors.groupingBy(
                        c -> c.cbrCase().outcome(),
                        Collectors.summingDouble(ScoredCbrCase::score)));

        double totalScore = scoresByOutcome.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalScore == 0) return new Prediction("UNKNOWN", 0);

        var winner = scoresByOutcome.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
        return new Prediction(winner.getKey(), winner.getValue() / totalScore);
    }

    @SuppressWarnings("unchecked")
    private static Instant defaultEarliestEnrollment(UUID siteId, String tenantId) {
        return PatientEnrollment.<PatientEnrollment>find("siteId = ?1 AND tenantId = ?2 AND enrolledAt IS NOT NULL ORDER BY enrolledAt ASC", siteId, tenantId)
                .firstResultOptional()
                .map(e -> e.enrolledAt)
                .orElse(null);
    }
}
