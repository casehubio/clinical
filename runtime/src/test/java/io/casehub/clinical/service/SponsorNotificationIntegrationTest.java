package io.casehub.clinical.service;

import io.casehub.clinical.api.SponsorNotifier;
import io.casehub.clinical.api.model.*;
import io.casehub.clinical.api.spi.PiIdentityResolver;
import io.casehub.clinical.entity.*;
import io.casehub.qhorus.api.message.MessageType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration test for the durable notification path.
 *
 * <p>With {@code DurableSponsorNotifier}, {@code SponsorNotificationListener.onDeviationResolved()}
 * persists a PENDING entity and returns — delivery happens via the scheduler, not synchronously.
 * This test verifies the full CDI chain:
 * <ol>
 *   <li>CDI event → SponsorNotificationListener → DurableSponsorNotifier.notify() → PENDING entity</li>
 *   <li>CDI resolution: SponsorNotifier resolves to DurableSponsorNotifier (sole implementation)</li>
 *   <li>Direct delivery via SponsorNotificationDeliveryService → DELIVERED entity</li>
 * </ol>
 */
@QuarkusTest
class SponsorNotificationIntegrationTest {

    @Inject PiResponseListener piResponseListener;
    @Inject SponsorNotifier sponsorNotifier;
    @Inject SponsorNotificationDeliveryService deliveryService;
    @Inject TestSlackConnector slackConnector;
    @InjectMock DeviationLedgerWriter ledgerWriter;
    @InjectMock SponsorNotificationLedgerWriter notificationLedgerWriter;
    @InjectMock PiIdentityResolver piIdentityResolver;

    private UUID deviationId;
    private String channelName;
    private UUID siteId;

    @BeforeEach
    @Transactional
    void setUp() {
        slackConnector.reset();

        // Suppress all ledger writes
        org.mockito.Mockito.doNothing().when(ledgerWriter)
                .writeResolutionEntry(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doNothing().when(ledgerWriter)
                .writeSponsorNotifiedEntry(
                        org.mockito.ArgumentMatchers.any(UUID.class),
                        org.mockito.ArgumentMatchers.any(UUID.class),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(Instant.class),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());

        when(piIdentityResolver.resolveFormalName("dr-jones@v1")).thenReturn("Dr. Jones");

        final UUID trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        deviationId = UUID.randomUUID();
        channelName = "clinical/deviation/dev-" + deviationId + "/pi-oversight";

        final ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.protocolId = "ONCO-001";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "Roche";
        trial.targetEnrollment = 100;
        trial.status = TrialStatus.ACTIVE;
        trial.sponsorNotificationConnectorId = "slack";
        trial.sponsorNotificationDestination = "https://hooks.slack.com/integration-test";
        trial.persist();

        final TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = "dr-jones@v1";
        site.status = SiteStatus.ACTIVE;
        site.persist();

        final ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = deviationId;
        dev.siteId = siteId;
        dev.deviationType = "INFORMED_CONSENT";
        dev.severity = DeviationSeverity.MAJOR;
        dev.escalationRequirement = EscalationRequirement.SPONSOR_NOTIFICATION;
        dev.piApprovalStatus = PiApprovalStatus.COMMANDED;
        dev.piCommandChannelName = channelName;
        dev.commandedAt = Instant.now();
        dev.responseDeadline = Instant.now().plusSeconds(3600);
        dev.persist();
    }

    @Test
    void cdi_resolves_DurableSponsorNotifier_as_sole_SponsorNotifier_implementation() {
        assertThat(sponsorNotifier).isInstanceOf(DurableSponsorNotifier.class);
    }

    @Test
    void pi_approval_creates_pending_notification_entity() {
        piResponseListener.process(channelName, MessageType.DONE, "dr-jones@v1");

        // SponsorNotificationListener is @ObservesAsync — fires on a CDI worker thread
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> findNotification() != null);

        final io.casehub.clinical.entity.SponsorNotification n = findNotification();
        assertThat(n.status).isEqualTo(SponsorNotificationStatus.PENDING);
        assertThat(n.deviationId).isEqualTo(deviationId);
        assertThat(n.connectorId).isEqualTo("slack");
        assertThat(n.piDisplayName).isEqualTo("Dr. Jones");
    }

    @Test
    void pending_notification_delivered_by_delivery_service() {
        piResponseListener.process(channelName, MessageType.DONE, "dr-jones@v1");

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> findNotification() != null);
        final io.casehub.clinical.entity.SponsorNotification n = findNotification();
        deliveryService.attemptDelivery(n.id);

        assertThat(slackConnector.sent()).hasSize(1);
        assertThat(slackConnector.sent().get(0).destination())
                .isEqualTo("https://hooks.slack.com/integration-test");
        assertThat(slackConnector.sent().get(0).body())
                .contains("INFORMED_CONSENT")
                .contains("Dr. Jones")
                .doesNotContain("dr-jones@v1")
                .contains("corrective action committed");
        assertThat(findNotification().status).isEqualTo(SponsorNotificationStatus.DELIVERED);
    }

    @Test
    void pi_rejection_also_creates_pending_notification() {
        piResponseListener.process(channelName, MessageType.DECLINE, "dr-jones@v1");

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> findNotification() != null);

        final io.casehub.clinical.entity.SponsorNotification n = findNotification();
        assertThat(n.status).isEqualTo(SponsorNotificationStatus.PENDING);
        assertThat(n.terminalStatus).isEqualTo(PiApprovalStatus.REJECTED);
    }

    @Test
    void rejected_deviation_notification_body_contains_refused() {
        piResponseListener.process(channelName, MessageType.DECLINE, "dr-jones@v1");

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> findNotification() != null);
        final io.casehub.clinical.entity.SponsorNotification n = findNotification();
        deliveryService.attemptDelivery(n.id);

        assertThat(slackConnector.sent().get(0).body())
                .contains("refused to authorise")
                .contains("Dr. Jones");
    }

    @Transactional
    io.casehub.clinical.entity.SponsorNotification findNotification() {
        return io.casehub.clinical.entity.SponsorNotification
                .<io.casehub.clinical.entity.SponsorNotification>find("deviationId", deviationId)
                .firstResult();
    }
}
