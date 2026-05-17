package io.casehub.clinical.service;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.spi.DeviationContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class DefaultDeviationResponsePolicyTest {

    @Inject
    DefaultDeviationResponsePolicy policy;

    private DeviationContext ctx(DeviationSeverity severity) {
        return new DeviationContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "PROT-001", TrialPhase.PHASE_II, severity, "sample-window");
    }

    @Test
    void minorDeviationGets7DayDeadlineAndNoEscalation() {
        var req = policy.evaluate(ctx(DeviationSeverity.MINOR));
        assertThat(req.piResponseDeadline()).isEqualTo(Duration.ofHours(168));
        assertThat(req.escalationRequirement()).isEqualTo(EscalationRequirement.NONE);
    }

    @Test
    void majorDeviationGets72hDeadlineAndSponsorNotification() {
        var req = policy.evaluate(ctx(DeviationSeverity.MAJOR));
        assertThat(req.piResponseDeadline()).isEqualTo(Duration.ofHours(72));
        assertThat(req.escalationRequirement()).isEqualTo(EscalationRequirement.SPONSOR_NOTIFICATION);
    }

    @Test
    void criticalDeviationGets24hDeadlineAndIrbReview() {
        var req = policy.evaluate(ctx(DeviationSeverity.CRITICAL));
        assertThat(req.piResponseDeadline()).isEqualTo(Duration.ofHours(24));
        assertThat(req.escalationRequirement()).isEqualTo(EscalationRequirement.IRB_REVIEW);
    }
}
