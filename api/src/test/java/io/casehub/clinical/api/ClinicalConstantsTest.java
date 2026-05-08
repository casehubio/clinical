package io.casehub.clinical.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ClinicalConstantsTest {

    @Test
    void capability_constants_are_kebab_case() {
        assertThat(ClinicalCapabilities.ELIGIBILITY_SCREENING).isEqualTo("eligibility-screening");
        assertThat(ClinicalCapabilities.SAFETY_MONITORING).isEqualTo("safety-monitoring");
        assertThat(ClinicalCapabilities.PROTOCOL_REVIEW).isEqualTo("protocol-review");
        assertThat(ClinicalCapabilities.IRB_CONSULTATION).isEqualTo("irb-consultation");
        assertThat(ClinicalCapabilities.PI_AUTHORISATION).isEqualTo("pi-authorisation");
        assertThat(ClinicalCapabilities.DATA_SAFETY_MONITORING).isEqualTo("data-safety-monitoring");
        assertThat(ClinicalCapabilities.REGULATORY_SUBMISSION).isEqualTo("regulatory-submission");
        assertThat(ClinicalCapabilities.TRIAL_SUPERVISOR).isEqualTo("trial-supervisor");
    }

    @Test
    void trust_dimension_constants_are_kebab_case() {
        assertThat(ClinicalTrustDimensions.SAFETY_ACCURACY).isEqualTo("safety-accuracy");
        assertThat(ClinicalTrustDimensions.ELIGIBILITY_PRECISION).isEqualTo("eligibility-precision");
        assertThat(ClinicalTrustDimensions.PROTOCOL_ADHERENCE).isEqualTo("protocol-adherence");
    }
}
