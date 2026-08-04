package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrFeatureSchema;
import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ClinicalCbrSchemaInitializerTest {

    private CbrCaseMemoryStore store;
    private ClinicalCbrSchemaInitializer initializer;

    @BeforeEach
    void setUp() {
        store = mock(CbrCaseMemoryStore.class);
        initializer = new ClinicalCbrSchemaInitializer(store);
    }

    @Test
    void onStartup_registersThreeSchemas() {
        initializer.onStartup(mock(StartupEvent.class));

        verify(store, times(6)).registerSchema(any(CbrFeatureSchema.class));
    }

    @Test
    void aeSchema_hasCorrectFieldsAndCaseType() {
        final ArgumentCaptor<CbrFeatureSchema> captor = ArgumentCaptor.forClass(CbrFeatureSchema.class);
        initializer.onStartup(mock(StartupEvent.class));

        verify(store, times(6)).registerSchema(captor.capture());

        final var schemas = captor.getAllValues();
        final var aeSchema = schemas.stream()
                                    .filter(s -> s.caseType().equals("clinical-ae"))
                                    .findFirst()
                                    .orElseThrow();

        assertThat(aeSchema.fields()).hasSize(15);
        assertThat(aeSchema.fields().stream().map(f -> f.name()))
                .containsExactlyInAnyOrder("grade", "eventType", "trialPhase", "unexpected",
                                           "suspected", "treatmentArm", "priorAeCount",
                                           "safetyReviewOutcome", "dsmbEscalated", "indReportFiled", "susarOversight",
                                           "siteEnrollmentCount", "siteTargetEnrollment", "agentTrustScore", "mergeCount");

        // Verify numeric field constraints
        final var gradeField = (io.casehub.neocortex.memory.cbr.FeatureField.Numeric) aeSchema.fields().stream()
                                                                                              .filter(f -> f.name().equals("grade"))
                                                                                              .findFirst()
                                                                                              .orElseThrow();
        assertThat(gradeField.min()).isEqualTo(1.0);
        assertThat(gradeField.max()).isEqualTo(5.0);
    }

    @Test
    void deviationSchema_hasCorrectFieldsAndCaseType() {
        final ArgumentCaptor<CbrFeatureSchema> captor = ArgumentCaptor.forClass(CbrFeatureSchema.class);
        initializer.onStartup(mock(StartupEvent.class));

        verify(store, times(6)).registerSchema(captor.capture());

        final var schemas = captor.getAllValues();
        final var deviationSchema = schemas.stream()
            .filter(s -> s.caseType().equals("clinical-deviation"))
            .findFirst()
            .orElseThrow();

        assertThat(deviationSchema.fields()).hasSize(5);
        assertThat(deviationSchema.fields().stream().map(f -> f.name()))
            .containsExactlyInAnyOrder("deviationType", "severity", "escalationRequirement",
                "piDecision", "irbDecision");
    }

    @Test
    void amendmentSchema_hasNoFeaturesButCorrectCaseType() {
        final ArgumentCaptor<CbrFeatureSchema> captor = ArgumentCaptor.forClass(CbrFeatureSchema.class);
        initializer.onStartup(mock(StartupEvent.class));

        verify(store, times(6)).registerSchema(captor.capture());

        final var schemas = captor.getAllValues();
        final var amendmentSchema = schemas.stream()
            .filter(s -> s.caseType().equals("clinical-amendment"))
            .findFirst()
            .orElseThrow();

        assertThat(amendmentSchema.fields()).isEmpty();
    }

    @Test
    void aeTrajectorySchema_hasTimeSeriesFieldWithDtwAndTrend() {
        final ArgumentCaptor<CbrFeatureSchema> captor = ArgumentCaptor.forClass(CbrFeatureSchema.class);
        initializer.onStartup(mock(StartupEvent.class));
        verify(store, times(6)).registerSchema(captor.capture());

        final var schema = captor.getAllValues().stream()
                                 .filter(s -> "clinical-ae-trajectory".equals(s.caseType()))
                                 .findFirst().orElseThrow();

        assertThat(schema.fields().stream().map(f -> f.name()))
                .contains("grade", "eventType", "aeTrajectory");

        final var tsField = schema.fields().stream()
                                  .filter(f -> f instanceof io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries)
                                  .map(f -> (io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries) f)
                                  .findFirst().orElseThrow();
        assertThat(tsField.name()).isEqualTo("aeTrajectory");
        assertThat(tsField.timestampField()).isEqualTo("ts");
        assertThat(tsField.similaritySpec()).isInstanceOf(io.casehub.neocortex.memory.cbr.SimilaritySpec.DtwSpec.class);
        assertThat(tsField.trendSpec()).isNotNull();
        assertThat(tsField.trendSpec().types()).contains(
                io.casehub.neocortex.memory.cbr.TrendType.SLOPE,
                io.casehub.neocortex.memory.cbr.TrendType.ACCELERATION,
                io.casehub.neocortex.memory.cbr.TrendType.CHANGE_POINTS);
    }

    @Test
    void siteEnrollmentSchema_hasTimeSeriesFieldWithDtwAndTrend() {
        final ArgumentCaptor<CbrFeatureSchema> captor = ArgumentCaptor.forClass(CbrFeatureSchema.class);
        initializer.onStartup(mock(StartupEvent.class));
        verify(store, times(6)).registerSchema(captor.capture());

        final var schema = captor.getAllValues().stream()
                                 .filter(s -> "clinical-site-enrollment".equals(s.caseType()))
                                 .findFirst().orElseThrow();

        final var tsField = schema.fields().stream()
                                  .filter(f -> f instanceof io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries)
                                  .map(f -> (io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries) f)
                                  .findFirst().orElseThrow();
        assertThat(tsField.name()).isEqualTo("enrollmentRate");
        assertThat(tsField.timestampField()).isEqualTo("ts");
        assertThat(tsField.similaritySpec()).isInstanceOf(io.casehub.neocortex.memory.cbr.SimilaritySpec.DtwSpec.class);
    }

    @Test
    void trialSafetySchema_hasCorrectFieldsAndCaseType() {
        final ArgumentCaptor<CbrFeatureSchema> captor = ArgumentCaptor.forClass(CbrFeatureSchema.class);
        initializer.onStartup(mock(StartupEvent.class));
        verify(store, times(6)).registerSchema(captor.capture());

        final var schema = captor.getAllValues().stream()
                                 .filter(s -> "clinical-trial-safety".equals(s.caseType()))
                                 .findFirst().orElseThrow();

        assertThat(schema.fields()).hasSize(7);
        assertThat(schema.fields().stream().map(f -> f.name()))
                .containsExactlyInAnyOrder("trialPhase", "aggregationPeriodDays", "siteCount",
                                           "affectedSiteCount", "dominantGrade", "dominantEventType",
                                           "signalType");
    }


}
