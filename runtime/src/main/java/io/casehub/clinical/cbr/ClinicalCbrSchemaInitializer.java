package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrFeatureSchema;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.SimilaritySpec;
import io.casehub.neocortex.memory.cbr.TrendSpec;
import io.casehub.neocortex.memory.cbr.TrendType;
import io.casehub.neocortex.memory.cbr.WarpingConstraint;
import io.quarkus.runtime.StartupEvent;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Registers CBR feature schemas at application startup.
 * <p>
 * Three schemas:
 * <ul>
 * <li>clinical-ae — adverse event precedents (11 features: grade, eventType, ...)</li>
 * <li>clinical-deviation — protocol deviation precedents (5 features: deviationType, ...)</li>
 * <li>clinical-amendment — protocol amendment precedents (0 features, pure textual)</li>
 * </ul>
 */
@ApplicationScoped
public class ClinicalCbrSchemaInitializer {

    private static final Logger LOG = Logger.getLogger(ClinicalCbrSchemaInitializer.class);

    private final CbrCaseMemoryStore store;

    @Inject
    public ClinicalCbrSchemaInitializer(final CbrCaseMemoryStore store) {
        this.store = store;
    }

    void onStartup(@Observes final StartupEvent event) {
        LOG.info("Registering CBR schemas: clinical-ae, clinical-deviation, clinical-amendment, clinical-ae-trajectory, clinical-site-enrollment");
        store.registerSchema(aeSchema());
        store.registerSchema(deviationSchema());
        store.registerSchema(amendmentSchema());
        store.registerSchema(aeTrajectorySchema());
        store.registerSchema(siteEnrollmentSchema());
    }

    static CbrFeatureSchema aeSchema() {
        return CbrFeatureSchema.of("clinical-ae",
                                   FeatureField.numeric("grade", 1, 5),
                                   FeatureField.categoricalList("eventType"),
                                   FeatureField.categorical("trialPhase"),
                                   FeatureField.categorical("unexpected"),
                                   FeatureField.categorical("suspected"),
                                   FeatureField.categorical("treatmentArm"),
                                   FeatureField.categorical("priorAeCount"),
                                   FeatureField.categorical("safetyReviewOutcome"),
                                   FeatureField.categorical("dsmbEscalated"),
                                   FeatureField.categorical("indReportFiled"),
                                   FeatureField.categorical("susarOversight"));
    }

    static CbrFeatureSchema deviationSchema() {
        return CbrFeatureSchema.of("clinical-deviation",
            FeatureField.categorical("deviationType"),
            FeatureField.categorical("severity"),
            FeatureField.categorical("escalationRequirement"),
            FeatureField.categorical("piDecision"),
            FeatureField.categorical("irbDecision"));
    }

    static CbrFeatureSchema amendmentSchema() {
        return CbrFeatureSchema.of("clinical-amendment"); // no features — pure text
    }

    public static CbrFeatureSchema aeTrajectorySchema() {
        return CbrFeatureSchema.of("clinical-ae-trajectory",
                                   FeatureField.numeric("grade", 1, 5),
                                   FeatureField.categoricalList("eventType"),
                                   FeatureField.categorical("trialPhase"),
                                   FeatureField.categorical("unexpected"),
                                   FeatureField.categorical("suspected"),
                                   FeatureField.timeSeries("aeTrajectory", "ts",
                                                           new SimilaritySpec.DtwSpec(new WarpingConstraint.SakoeChibaBand(3)),
                                                           new TrendSpec(Set.of(TrendType.SLOPE, TrendType.ACCELERATION, TrendType.CHANGE_POINTS), ChronoUnit.HOURS),
                                                           FeatureField.numeric("ts", 0, 7776000),
                                                           FeatureField.numeric("escalation", 0, 3),
                                                           FeatureField.numeric("susar", 0, 3),
                                                           FeatureField.numeric("regulatory", 0, 3)));
    }

    public static CbrFeatureSchema siteEnrollmentSchema() {
        return CbrFeatureSchema.of("clinical-site-enrollment",
                                   FeatureField.categorical("trialPhase"),
                                   FeatureField.timeSeries("enrollmentRate", "ts",
                                                           new SimilaritySpec.DtwSpec(new WarpingConstraint.SakoeChibaBand(3)),
                                                           new TrendSpec(Set.of(TrendType.SLOPE, TrendType.ACCELERATION, TrendType.CHANGE_POINTS), ChronoUnit.WEEKS),
                                                           FeatureField.numeric("ts", 0, 260),
                                                           FeatureField.numeric("cumulativeCount", 0, 10000),
                                                           FeatureField.numeric("periodCount", 0, 500)));
    }

}
