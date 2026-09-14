package io.casehub.clinical.agent;

import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelRegistry;
import io.casehub.platform.api.model.ModelTier;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClinicalModelTierResolutionTest {

    private Config config;
    private ModelRegistry modelRegistry;

    @BeforeEach
    void setUp() {
        config = mock(Config.class);
        modelRegistry = mock(ModelRegistry.class);
        when(config.getOptionalValue(any(), eq(String.class))).thenReturn(Optional.empty());
    }

    @Test
    void safetyMonitoringDefaultsToFlagship() {
        var descriptor = descriptorWith("claude-opus-4");
        when(modelRegistry.query(any(ModelQuery.class))).thenReturn(List.of(descriptor));

        String resolved = ClinicalModelTierResolver.resolveModel("safety-monitoring", config, modelRegistry);

        var captor = ArgumentCaptor.forClass(ModelQuery.class);
        verify(modelRegistry).query(captor.capture());
        assertThat(captor.getValue().tier()).isEqualTo(ModelTier.FLAGSHIP);
        assertThat(resolved).isEqualTo("claude-opus-4");
    }

    @Test
    void eligibilityScreeningDefaultsToStandard() {
        var descriptor = descriptorWith("claude-sonnet-5");
        when(modelRegistry.query(any(ModelQuery.class))).thenReturn(List.of(descriptor));

        String resolved = ClinicalModelTierResolver.resolveModel("eligibility-screening", config, modelRegistry);

        var captor = ArgumentCaptor.forClass(ModelQuery.class);
        verify(modelRegistry).query(captor.capture());
        assertThat(captor.getValue().tier()).isEqualTo(ModelTier.STANDARD);
        assertThat(resolved).isEqualTo("claude-sonnet-5");
    }

    @Test
    void explicitModelOverridesTier() {
        when(config.getOptionalValue(eq("casehub.clinical.agent.safety-monitoring.model"), eq(String.class)))
                .thenReturn(Optional.of("claude-haiku-4-5"));

        String resolved = ClinicalModelTierResolver.resolveModel("safety-monitoring", config, modelRegistry);

        assertThat(resolved).isEqualTo("claude-haiku-4-5");
        verify(modelRegistry, never()).query(any());
    }

    @Test
    void explicitTierConfigOverridesDefault() {
        when(config.getOptionalValue(eq("casehub.clinical.agent.safety-monitoring.tier"), eq(String.class)))
                .thenReturn(Optional.of("STANDARD"));
        var descriptor = descriptorWith("claude-sonnet-5");
        when(modelRegistry.query(any(ModelQuery.class))).thenReturn(List.of(descriptor));

        String resolved = ClinicalModelTierResolver.resolveModel("safety-monitoring", config, modelRegistry);

        var captor = ArgumentCaptor.forClass(ModelQuery.class);
        verify(modelRegistry).query(captor.capture());
        assertThat(captor.getValue().tier()).isEqualTo(ModelTier.STANDARD);
        assertThat(resolved).isEqualTo("claude-sonnet-5");
    }

    @Test
    void fallsBackToSonnetWhenRegistryEmpty() {
        when(modelRegistry.query(any(ModelQuery.class))).thenReturn(List.of());

        String resolved = ClinicalModelTierResolver.resolveModel("safety-monitoring", config, modelRegistry);

        assertThat(resolved).isEqualTo("sonnet");
    }

    @Test
    void unknownCapabilityDefaultsToFlagship() {
        var descriptor = descriptorWith("claude-opus-4");
        when(modelRegistry.query(any(ModelQuery.class))).thenReturn(List.of(descriptor));

        String resolved = ClinicalModelTierResolver.resolveModel("unknown-capability", config, modelRegistry);

        var captor = ArgumentCaptor.forClass(ModelQuery.class);
        verify(modelRegistry).query(captor.capture());
        assertThat(captor.getValue().tier()).isEqualTo(ModelTier.FLAGSHIP);
    }

    @Test
    void allFourFlagshipCapabilitiesResolveFlagship() {
        var descriptor = descriptorWith("claude-opus-4");
        when(modelRegistry.query(any(ModelQuery.class))).thenReturn(List.of(descriptor));

        for (String key : List.of("safety-monitoring", "susar-criteria", "protocol-amendment", "trial-supervision")) {
            var captor = ArgumentCaptor.forClass(ModelQuery.class);
            ClinicalModelTierResolver.resolveModel(key, config, modelRegistry);
            verify(modelRegistry).query(captor.capture());
            assertThat(captor.getValue().tier()).as("Tier for " + key).isEqualTo(ModelTier.FLAGSHIP);
            org.mockito.Mockito.clearInvocations(modelRegistry);
            when(modelRegistry.query(any(ModelQuery.class))).thenReturn(List.of(descriptor));
        }
    }

    private ModelDescriptor descriptorWith(String apiModelId) {
        var descriptor = mock(ModelDescriptor.class);
        when(descriptor.apiModelId()).thenReturn(apiModelId);
        return descriptor;
    }
}
