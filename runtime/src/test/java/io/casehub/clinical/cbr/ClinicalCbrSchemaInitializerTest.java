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

        verify(store, times(3)).registerSchema(any(CbrFeatureSchema.class));
    }

    @Test
    void aeSchema_hasCorrectFieldsAndCaseType() {
        final ArgumentCaptor<CbrFeatureSchema> captor = ArgumentCaptor.forClass(CbrFeatureSchema.class);
        initializer.onStartup(mock(StartupEvent.class));

        verify(store, times(3)).registerSchema(captor.capture());

        final var schemas = captor.getAllValues();
        final var aeSchema = schemas.stream()
                                    .filter(s -> s.caseType().equals("clinical-ae"))
                                    .findFirst()
                                    .orElseThrow();

        assertThat(aeSchema.fields()).hasSize(11);
        assertThat(aeSchema.fields().stream().map(f -> f.name()))
                .containsExactlyInAnyOrder("grade", "eventType", "trialPhase", "unexpected",
                                           "suspected", "treatmentArm", "priorAeCount",
                                           "safetyReviewOutcome", "dsmbEscalated", "indReportFiled", "susarOversight");

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

        verify(store, times(3)).registerSchema(captor.capture());

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

        verify(store, times(3)).registerSchema(captor.capture());

        final var schemas = captor.getAllValues();
        final var amendmentSchema = schemas.stream()
            .filter(s -> s.caseType().equals("clinical-amendment"))
            .findFirst()
            .orElseThrow();

        assertThat(amendmentSchema.fields()).isEmpty();
    }
}
