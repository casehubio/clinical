package io.casehub.clinical.service;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Full channel-flow integration test: receiveHumanMessage → CDI event → PiResponseListener.
 * Blocked on casehubio/qhorus#153 (MessageReceivedEvent CDI hook in ChannelGateway).
 * Enable when qhorus ships the event and casehub-qhorus version is bumped.
 */
@QuarkusTest
@Disabled("casehubio/qhorus#153 — MessageReceivedEvent CDI hook not yet available")
class PiResponseListenerIntegrationTest {

    @Test
    void piApprovalViaChannelGatewayUpdatesDeviationStatus() {
        // TODO when qhorus#153 ships:
        // 1. reportDeviation() → COMMANDED
        // 2. channelGateway.receiveHumanMessage(channelRef,
        //        new InboundHumanMessage("pi-001", "{\"decision\":\"APPROVED\"}", Instant.now(), Map.of(), deviation.id.toString()))
        // 3. ClinicalInboundNormaliser maps to DONE
        // 4. messageService.send() auto-fulfills Commitment
        // 5. MessageReceivedEvent CDI event fires in receiveHumanMessage()
        // 6. PiResponseListener.process() invoked via @ObservesAsync
        // 7. Assert piApprovalStatus == APPROVED (MINOR) or ESCALATED (CRITICAL)
    }
}
