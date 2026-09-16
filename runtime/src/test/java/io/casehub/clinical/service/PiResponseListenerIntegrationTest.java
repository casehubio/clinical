package io.casehub.clinical.service;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.InboundHumanMessage;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full channel-flow integration test: receiveHumanMessage → MessageReceivedEvent CDI event → PiResponseListener.
 * Enabled when casehubio/qhorus#153 (MessageReceivedEvent CDI hook) shipped.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@io.quarkus.test.security.TestSecurity(user = "test-actor", roles = {io.casehub.clinical.api.ClinicalGroups.SPONSOR, io.casehub.clinical.api.ClinicalGroups.INVESTIGATOR, io.casehub.clinical.api.ClinicalGroups.COORDINATOR})
class PiResponseListenerIntegrationTest {

    @Inject ProtocolDeviationService deviationService;
    @Inject ChannelService channelService;
    @Inject ChannelGateway channelGateway;
    @Inject io.casehub.platform.testing.FixedCurrentPrincipal principal;
    @Inject
            jakarta.persistence.EntityManager                 em;


    private UUID siteId;
    private UUID minorDeviationId;
    private UUID criticalDeviationId;

    @BeforeAll
    @Transactional
    void setup() {
        UUID trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId; trial.protocolId = "INT-" + trialId; trial.phase = TrialPhase.PHASE_II;
        trial.sponsor = "S"; trial.targetEnrollment = 10; trial.status = TrialStatus.ACTIVE;
        trial.tenantId = principal.tenancyId();
        em.persist(trial);
        TrialSite site = new TrialSite();
        site.id = siteId; site.trialId = trialId; site.investigatorId = "pi-int";
        site.tenantId = principal.tenancyId();
        em.persist(site);
    }

    @Test
    @Order(1)
    @Transactional
    void reportMinorDeviationReachesCommandedState() {
        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = UUID.randomUUID();
        dev.siteId = siteId;
        dev.deviationType = "sample-window";
        dev.severity = DeviationSeverity.MINOR;
        minorDeviationId = dev.id;
        deviationService.reportDeviation(dev);

        ProtocolDeviation loaded = em.find(ProtocolDeviation.class, dev.id);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.COMMANDED);
        assertThat(loaded.piCommandChannelName).contains("/pi-oversight");
    }

    @Test
    @Order(2)
    @Transactional
    void reportCriticalDeviationReachesCommandedState() {
        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = UUID.randomUUID();
        dev.siteId = siteId;
        dev.deviationType = "eligibility-breach";
        dev.severity = DeviationSeverity.CRITICAL;
        criticalDeviationId = dev.id;
        deviationService.reportDeviation(dev);

        ProtocolDeviation loaded = em.find(ProtocolDeviation.class, dev.id);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.COMMANDED);
    }

    @Test
    @Order(3)
    void piApprovalViaChannelGateway_minorDeviationMovesToApproved() throws Exception {
        assertThat(minorDeviationId).as("set by Order(1)").isNotNull();

        ProtocolDeviation dev = em.find(ProtocolDeviation.class, minorDeviationId);
        var channelRef = channelService.findByName(dev.piCommandChannelName)
            .map(c -> new ChannelRef(c.id(), c.name()))
            .orElseThrow(() -> new AssertionError("channel not found: " + dev.piCommandChannelName));

        channelGateway.receiveHumanMessage(channelRef,
            new InboundHumanMessage("pi-int", "{\"decision\":\"APPROVED\"}", Instant.now(),
                Map.of(), minorDeviationId.toString(), null));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            QuarkusTransaction.requiringNew().call(() -> {
                ProtocolDeviation loaded = em.find(ProtocolDeviation.class, minorDeviationId);
                assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.APPROVED);
                return null;
            }));
    }

    @Test
    @Order(4)
    void piApprovalViaChannelGateway_criticalDeviationMovesToEscalated() throws Exception {
        assertThat(criticalDeviationId).as("set by Order(2)").isNotNull();

        ProtocolDeviation dev = em.find(ProtocolDeviation.class, criticalDeviationId);
        var channelRef = channelService.findByName(dev.piCommandChannelName)
            .map(c -> new ChannelRef(c.id(), c.name()))
            .orElseThrow(() -> new AssertionError("channel not found: " + dev.piCommandChannelName));

        channelGateway.receiveHumanMessage(channelRef,
            new InboundHumanMessage("pi-int", "{\"decision\":\"APPROVED\"}", Instant.now(),
                Map.of(), criticalDeviationId.toString(), null));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            QuarkusTransaction.requiringNew().call(() -> {
                ProtocolDeviation loaded = em.find(ProtocolDeviation.class, criticalDeviationId);
                assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.ESCALATED);
                return null;
            }));
    }
}
