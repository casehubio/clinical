package io.casehub.clinical.routing;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.clinical.api.ClinicalCapabilities;
import io.casehub.clinical.api.ClinicalTrustDimensions;
import org.junit.jupiter.api.Test;

class ClinicalTrustRoutingPolicyProviderTest {

    private final ClinicalTrustRoutingPolicyProvider provider = new ClinicalTrustRoutingPolicyProvider();

    @Test
    void safety_monitoring_has_strict_threshold_and_tight_margin() {
        TrustRoutingPolicy policy = provider.forCapability(ClinicalCapabilities.SAFETY_MONITORING);
        assertThat(policy.threshold()).isEqualTo(0.75);
        assertThat(policy.minimumObservations()).isEqualTo(20);
        assertThat(policy.borderlineMargin()).isEqualTo(0.05);
        assertThat(policy.blendFactor()).isEqualTo(0.7);
        assertThat(policy.qualityFloors()).containsKey(ClinicalTrustDimensions.SAFETY_ACCURACY);
        assertThat(policy.qualityFloors()).containsEntry(ClinicalTrustDimensions.SAFETY_ACCURACY, 0.70);
    }

    @Test
    void safety_monitoring_requires_20_observations_before_trust_kicks_in() {
        TrustRoutingPolicy policy = provider.forCapability(ClinicalCapabilities.SAFETY_MONITORING);
        assertThat(policy.isBootstrap(19)).isTrue();
        assertThat(policy.isBootstrap(20)).isFalse();
    }

    @Test
    void eligibility_screening_has_moderate_threshold() {
        TrustRoutingPolicy policy = provider.forCapability(ClinicalCapabilities.ELIGIBILITY_SCREENING);
        assertThat(policy.threshold()).isEqualTo(0.70);
        assertThat(policy.minimumObservations()).isEqualTo(15);
        assertThat(policy.isBootstrap(14)).isTrue();
        assertThat(policy.isBootstrap(15)).isFalse();
    }

    @Test
    void protocol_review_has_conservative_observation_count() {
        TrustRoutingPolicy policy = provider.forCapability(ClinicalCapabilities.PROTOCOL_REVIEW);
        assertThat(policy.minimumObservations()).isEqualTo(25);
        assertThat(policy.isBootstrap(24)).isTrue();
        assertThat(policy.isBootstrap(25)).isFalse();
    }

    @Test
    void unconfigured_capabilities_return_default_non_null() {
        TrustRoutingPolicy policy = provider.forCapability(ClinicalCapabilities.IRB_CONSULTATION);
        assertThat(policy).isNotNull();
        assertThat(policy).isEqualTo(TrustRoutingPolicy.DEFAULT);
    }

    @Test
    void eligibility_screening_quality_floor_correct() {
        TrustRoutingPolicy policy = provider.forCapability(ClinicalCapabilities.ELIGIBILITY_SCREENING);
        assertThat(policy.qualityFloors()).containsEntry(ClinicalTrustDimensions.ELIGIBILITY_PRECISION, 0.65);
    }

    @Test
    void protocol_review_quality_floor_correct() {
        TrustRoutingPolicy policy = provider.forCapability(ClinicalCapabilities.PROTOCOL_REVIEW);
        assertThat(policy.qualityFloors()).containsEntry(ClinicalTrustDimensions.PROTOCOL_ADHERENCE, 0.60);
    }

    @Test
    void all_8_capabilities_return_non_null() {
        for (String cap : new String[]{
                ClinicalCapabilities.SAFETY_MONITORING,
                ClinicalCapabilities.ELIGIBILITY_SCREENING,
                ClinicalCapabilities.PROTOCOL_REVIEW,
                ClinicalCapabilities.IRB_CONSULTATION,
                ClinicalCapabilities.PI_AUTHORISATION,
                ClinicalCapabilities.DATA_SAFETY_MONITORING,
                ClinicalCapabilities.REGULATORY_SUBMISSION,
                ClinicalCapabilities.TRIAL_SUPERVISOR}) {
            assertThat(provider.forCapability(cap)).isNotNull();
        }
    }
}
