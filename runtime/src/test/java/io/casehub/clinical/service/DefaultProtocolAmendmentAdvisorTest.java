package io.casehub.clinical.service;

import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.api.spi.ProtocolAmendmentContext;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultProtocolAmendmentAdvisorTest {

    DefaultProtocolAmendmentAdvisor advisor = new DefaultProtocolAmendmentAdvisor();

    @Test
    void always_returns_PROCEED() {
        var ctx = new ProtocolAmendmentContext(UUID.randomUUID(), UUID.randomUUID(),
            "Dose escalation amendment v2", Map.of());
        assertThat(advisor.advise(ctx)).isEqualTo(AmendmentRecommendation.PROCEED);
    }
}
