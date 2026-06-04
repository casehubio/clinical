package io.casehub.clinical.service;

import io.casehub.clinical.api.SponsorNotificationRequest;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for DefaultSponsorNotifier.buildBody() — no container, no I/O.
 * Verifies that piDisplayName (not raw piId) appears in regulated sponsor notification bodies.
 */
class DefaultSponsorNotifierBodyTest {

    private final DefaultSponsorNotifier notifier = new DefaultSponsorNotifier(java.util.List.of());

    @Test
    void escalated_body_uses_pi_display_name_not_raw_id() {
        SponsorNotificationRequest req = request(PiApprovalStatus.ESCALATED, "claude:pi@v1", "Dr. Jane Smith");
        String body = notifier.buildBody(req);
        assertThat(body).contains("Dr. Jane Smith");
        assertThat(body).doesNotContain("claude:pi@v1");
    }

    @Test
    void rejected_body_uses_pi_display_name_not_raw_id() {
        SponsorNotificationRequest req = request(PiApprovalStatus.REJECTED, "claude:pi@v1", "Dr. Jane Smith");
        String body = notifier.buildBody(req);
        assertThat(body).contains("Dr. Jane Smith");
        assertThat(body).doesNotContain("claude:pi@v1");
    }

    @Test
    void expired_body_does_not_reference_pi_name() {
        SponsorNotificationRequest req = request(PiApprovalStatus.EXPIRED, null, null);
        String body = notifier.buildBody(req);
        assertThat(body).contains("expired");
        assertThat(body).doesNotContain("null");
    }

    private SponsorNotificationRequest request(PiApprovalStatus status, String piId, String piDisplayName) {
        return new SponsorNotificationRequest(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "CONSENT_DEVIATION", DeviationSeverity.MAJOR, status,
            piId, piDisplayName,
            "slack", "https://hooks.slack.com/test"
        );
    }
}
