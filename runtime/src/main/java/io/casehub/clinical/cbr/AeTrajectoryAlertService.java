package io.casehub.clinical.cbr;

import io.casehub.clinical.api.AeTrajectoryAlertEvent;
import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.neocortex.memory.cbr.CbrFilter;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class AeTrajectoryAlertService {

    private static final Logger LOG = Logger.getLogger(AeTrajectoryAlertService.class);

    private final AeTrajectoryBuilder trajectoryBuilder;
    private final ClinicalCbrService cbrService;
    private final Event<AeTrajectoryAlertEvent> alertEvents;
    private Function<UUID, AdverseEvent> entityFinder = id -> AdverseEvent.findById(id);

    @ConfigProperty(name = "casehub.clinical.trajectory.alert.min-matches", defaultValue = "2")
    int minMatches;

    @ConfigProperty(name = "casehub.clinical.trajectory.alert.min-similarity", defaultValue = "0.5")
    double minSimilarity;

    @ConfigProperty(name = "casehub.clinical.trajectory.alert.min-probability", defaultValue = "0.6")
    double minProbability;

    @Inject
    public AeTrajectoryAlertService(AeTrajectoryBuilder trajectoryBuilder,
                                     ClinicalCbrService cbrService,
                                     Event<AeTrajectoryAlertEvent> alertEvents) {
        this.trajectoryBuilder = trajectoryBuilder;
        this.cbrService = cbrService;
        this.alertEvents = alertEvents;
    }

    void setEntityFinder(Function<UUID, AdverseEvent> finder) {
        this.entityFinder = finder;
    }

    public Optional<AeTrajectoryAlertEvent> evaluate(UUID aeId, String tenantId) {
        try {
            AdverseEvent ae = entityFinder.apply(aeId);
            if (ae == null) {
                LOG.debugf("AeTrajectoryAlertService: AE not found for aeId=%s", aeId);
                return Optional.empty();
            }
            var trajectory = trajectoryBuilder.buildPartialTrajectory(ae, tenantId);

            Map<String, FeatureValue> features = new LinkedHashMap<>();
            features.put("grade", FeatureValue.number(ae.grade != null ? ae.grade.ordinal() + 1 : 0));
            features.put("trialPhase", FeatureValue.string("UNKNOWN"));
            features.put("unexpected", FeatureValue.string(String.valueOf(ae.unexpected)));
            features.put("suspected", FeatureValue.string(String.valueOf(ae.suspected)));
            features.put("aeTrajectory", FeatureValue.structList(trajectory));

            CbrQuery query = CbrQuery.of(tenantId, ClinicalCbrDomains.AE_TRAJECTORY, Path.root(),
                            "clinical-ae-trajectory", features, 10)
                    .withMinSimilarity(minSimilarity)
                    .withFilter("eventType", CbrFilter.contains(ae.eventType != null ? ae.eventType : "UNKNOWN"));

            AuditedRetrievalResult<PlanCbrCase> result = cbrService.retrieveWithAudit(
                    query, PlanCbrCase.class, ae.enrollmentId, ClinicalActors.CLINICAL_SERVICE);

            if (result.cases().size() < minMatches) return Optional.empty();

            var prediction = predictOutcome(result.cases());
            if (prediction.probability < minProbability) return Optional.empty();

            var event = new AeTrajectoryAlertEvent(
                    aeId, ae.enrollmentId, null, ae.grade,
                    result.cases().size(), result.cases().get(0).score(),
                    prediction.outcome, prediction.probability,
                    result.traceId(), tenantId);
            alertEvents.fireAsync(event);
            return Optional.of(event);
        } catch (Exception e) {
            LOG.warnf(e, "AeTrajectoryAlertService: evaluation failed for aeId=%s", aeId);
            return Optional.empty();
        }
    }

    record Prediction(String outcome, double probability) {}

    Prediction predictOutcome(List<ScoredCbrCase<PlanCbrCase>> cases) {
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
}
