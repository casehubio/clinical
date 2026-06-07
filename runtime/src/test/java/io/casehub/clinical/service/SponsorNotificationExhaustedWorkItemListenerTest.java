package io.casehub.clinical.service;

import io.casehub.clinical.api.SponsorNotificationExhaustedEvent;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.ClinicalActors;
import io.casehub.work.runtime.model.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class SponsorNotificationExhaustedWorkItemListenerTest {

    @Inject SponsorNotificationExhaustedWorkItemListener listener;
    @InjectMock WorkItemService workItemService;
    @InjectMock Clock clock;

    private static final Instant FIXED = Instant.parse("2026-06-07T10:00:00Z");

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED);
    }

    @Test
    void critical_deviation_gets_24h_claim_deadline() {
        listener.onExhausted(exhaustedEvent(DeviationSeverity.CRITICAL));

        final WorkItemCreateRequest req = captureCreated();
        assertThat(req.claimDeadline).isEqualTo(FIXED.plus(Duration.ofHours(24)));
    }

    @Test
    void major_deviation_gets_72h_claim_deadline() {
        listener.onExhausted(exhaustedEvent(DeviationSeverity.MAJOR));

        final WorkItemCreateRequest req = captureCreated();
        assertThat(req.claimDeadline).isEqualTo(FIXED.plus(Duration.ofHours(72)));
    }

    @Test
    void minor_deviation_gets_72h_claim_deadline() {
        listener.onExhausted(exhaustedEvent(DeviationSeverity.MINOR));

        final WorkItemCreateRequest req = captureCreated();
        assertThat(req.claimDeadline).isEqualTo(FIXED.plus(Duration.ofHours(72)));
    }

    @Test
    void workitem_routed_to_site_coordinators() {
        listener.onExhausted(exhaustedEvent(DeviationSeverity.MAJOR));

        final WorkItemCreateRequest req = captureCreated();
        assertThat(req.candidateGroups).isEqualTo("site-coordinators");
    }

    @Test
    void workitem_category_is_sponsor_notification_escalation() {
        listener.onExhausted(exhaustedEvent(DeviationSeverity.MAJOR));

        final WorkItemCreateRequest req = captureCreated();
        assertThat(req.category).isEqualTo("sponsor-notification-escalation");
    }

    @Test
    void workitem_payload_contains_deviation_id() {
        final UUID deviationId = UUID.randomUUID();
        listener.onExhausted(exhaustedEvent(DeviationSeverity.MAJOR, deviationId));

        final WorkItemCreateRequest req = captureCreated();
        assertThat(req.payload).contains(deviationId.toString());
    }

    @Test
    void workitem_has_high_priority_and_system_creator() {
        listener.onExhausted(exhaustedEvent(DeviationSeverity.MAJOR));

        final WorkItemCreateRequest req = captureCreated();
        assertThat(req.priority).isEqualTo(WorkItemPriority.HIGH);
        assertThat(req.createdBy).isEqualTo(ClinicalActors.CLINICAL_SERVICE);
    }

    @Test
    void workitem_payload_contains_all_required_fields() {
        final UUID notificationId = UUID.randomUUID();
        final UUID deviationId = UUID.randomUUID();
        final UUID trialId = UUID.randomUUID();
        final UUID siteId = UUID.randomUUID();
        final SponsorNotificationExhaustedEvent event = new SponsorNotificationExhaustedEvent(
                notificationId, deviationId, trialId, siteId,
                DeviationSeverity.MAJOR, PiApprovalStatus.REJECTED, "Timeout", 2);
        listener.onExhausted(event);

        final String payload = captureCreated().payload;
        assertThat(payload)
                .contains(notificationId.toString())
                .contains(deviationId.toString())
                .contains(trialId.toString())
                .contains(siteId.toString())
                .contains("MAJOR")
                .contains("REJECTED")
                .contains("Timeout")
                .contains("2");
    }

    @Test
    void null_failure_reason_produces_empty_string_in_payload_and_description() {
        final SponsorNotificationExhaustedEvent event = new SponsorNotificationExhaustedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                DeviationSeverity.MINOR, PiApprovalStatus.EXPIRED, null, 1);

        listener.onExhausted(event);

        final WorkItemCreateRequest req = captureCreated();
        assertThat(req.payload).doesNotContain("null");
        assertThat(req.description).doesNotContain("null");
    }

    @Test
    void workitem_creation_failure_does_not_propagate() {
        doThrow(new RuntimeException("DB unavailable")).when(workItemService).create(any());

        assertThatCode(() -> listener.onExhausted(exhaustedEvent(DeviationSeverity.MAJOR)))
                .doesNotThrowAnyException();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private WorkItemCreateRequest captureCreated() {
        final ArgumentCaptor<WorkItemCreateRequest> captor =
                ArgumentCaptor.forClass(WorkItemCreateRequest.class);
        verify(workItemService).create(captor.capture());
        return captor.getValue();
    }

    private static SponsorNotificationExhaustedEvent exhaustedEvent(final DeviationSeverity severity) {
        return exhaustedEvent(severity, UUID.randomUUID());
    }

    private static SponsorNotificationExhaustedEvent exhaustedEvent(
            final DeviationSeverity severity, final UUID deviationId) {
        return new SponsorNotificationExhaustedEvent(
                UUID.randomUUID(), deviationId, UUID.randomUUID(), UUID.randomUUID(),
                severity, PiApprovalStatus.ESCALATED, "Connection refused", 3);
    }
}
