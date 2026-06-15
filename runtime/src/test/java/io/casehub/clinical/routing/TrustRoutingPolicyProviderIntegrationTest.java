package io.casehub.clinical.routing;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.clinical.api.ClinicalCapabilities;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TrustRoutingPolicyProviderIntegrationTest {

    @Inject TrustRoutingPolicyProvider provider;

    @Test
    void cdi_injects_clinical_provider_not_default() {
        assertThat(provider).isInstanceOf(ClinicalTrustRoutingPolicyProvider.class);
    }

    @Test
    void safety_monitoring_policy_not_null() {
        assertThat(provider.forCapability(ClinicalCapabilities.SAFETY_MONITORING)).isNotNull();
    }

    @Test
    void unconfigured_capability_returns_default_not_null() {
        TrustRoutingPolicy policy = provider.forCapability(ClinicalCapabilities.IRB_CONSULTATION);
        assertThat(policy).isNotNull();
        assertThat(policy).isEqualTo(TrustRoutingPolicy.DEFAULT);
    }
}
