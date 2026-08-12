package io.casehub.clinical.service;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.MessageService;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommitmentLifecycleServiceTest {

    @Mock CommitmentReader commitmentReader;
    @Mock ChannelService channelService;
    @Mock MessageService messageService;
    @Mock CurrentPrincipal principal;
    @InjectMocks CommitmentLifecycleService service;

    private static final UUID DEV_ID = UUID.randomUUID();
    private static final UUID CHANNEL_ID = UUID.randomUUID();
    private static final String TENANT = "test-tenant";
    private static final Instant COMMANDED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant ACK_AT = Instant.parse("2026-01-01T11:00:00Z");
    private static final Instant RESOLVED_AT = Instant.parse("2026-01-01T12:00:00Z");

    private ProtocolDeviation deviation;

    @BeforeEach
    void setup() {
        deviation = new ProtocolDeviation();
        deviation.id = DEV_ID;
        deviation.tenantId = TENANT;
        deviation.siteId = UUID.randomUUID();
        deviation.deviationType = "DOSAGE";
        deviation.severity = DeviationSeverity.CRITICAL;
        deviation.piApprovalStatus = PiApprovalStatus.COMMANDED;
        deviation.piCommandChannelName = "clinical/deviation/dev-" + DEV_ID + "/pi-oversight";
        deviation.commandedAt = COMMANDED_AT;
        org.mockito.Mockito.lenient().when(principal.tenancyId()).thenReturn(TENANT);
    }

    private Commitment commitment(CommitmentState state, Instant ackAt, Instant resAt) {
        return Commitment.builder()
                .id(UUID.randomUUID())
                .correlationId(DEV_ID.toString())
                .channelId(CHANNEL_ID)
                .messageType(MessageType.COMMAND)
                .requester("clinical-service")
                .obligor("dr-smith")
                .state(state)
                .expiresAt(COMMANDED_AT.plusSeconds(86400))
                .acknowledgedAt(ackAt)
                .resolvedAt(resAt)
                .tenancyId(TENANT)
                .createdAt(COMMANDED_AT)
                .build();
    }

    private void stubCommitment(Commitment c) {
        when(commitmentReader.findByCorrelationId(DEV_ID.toString())).thenReturn(Optional.of(c));
        when(channelService.findByName(deviation.piCommandChannelName))
                .thenReturn(Optional.of(stubChannel()));
        when(messageService.history(CHANNEL_ID, 0L, 100)).thenReturn(List.of());
    }

    private Channel stubChannel() {
        return Channel.builder(deviation.piCommandChannelName).id(CHANNEL_ID)
                .tenancyId(TENANT).build();
    }

    @Test
    void openState_commandedCompletedRestPending() {
        var c = commitment(CommitmentState.OPEN, null, null);
        stubCommitment(c);
        var result = service.buildResponse(deviation, principal);
        assertTrue(result.isPresent());
        var resp = result.get();
        assertEquals("COMMANDED", resp.currentStage());
        assertEquals(4, resp.stages().size());
        assertEquals("completed", resp.stages().get(0).status());
        assertEquals("COMMANDED", resp.stages().get(0).key());
        assertEquals("clinical-service", resp.stages().get(0).actor());
        assertEquals("pending", resp.stages().get(1).status());
        assertEquals("pending", resp.stages().get(2).status());
        assertEquals("pending", resp.stages().get(3).status());
    }

    @Test
    void acknowledgedState_commandedAndAckCompleted() {
        var c = commitment(CommitmentState.ACKNOWLEDGED, ACK_AT, null);
        stubCommitment(c);
        var resp = service.buildResponse(deviation, principal).orElseThrow();
        assertEquals("ACKNOWLEDGED", resp.currentStage());
        assertEquals("completed", resp.stages().get(0).status());
        assertEquals("active", resp.stages().get(1).status());
        assertEquals("dr-smith", resp.stages().get(1).actor());
    }

    @Test
    void fulfilledState_doneCompleted() {
        var c = commitment(CommitmentState.FULFILLED, ACK_AT, RESOLVED_AT);
        stubCommitment(c);
        var resp = service.buildResponse(deviation, principal).orElseThrow();
        assertEquals("DONE", resp.currentStage());
        assertEquals("completed", resp.stages().get(0).status());
        assertEquals("completed", resp.stages().get(1).status());
        assertEquals("completed", findStage(resp, "DONE").status());
        assertEquals("dr-smith", findStage(resp, "DONE").actor());
    }

    @Test
    void fulfilledState_acknowledgedSkipped() {
        var c = commitment(CommitmentState.FULFILLED, null, RESOLVED_AT);
        stubCommitment(c);
        var resp = service.buildResponse(deviation, principal).orElseThrow();
        assertEquals("DONE", resp.currentStage());
        var ackStage = findStage(resp, "ACKNOWLEDGED");
        assertEquals("completed", ackStage.status());
        assertNull(ackStage.timestamp());
    }

    @Test
    void declinedState() {
        var c = commitment(CommitmentState.DECLINED, null, RESOLVED_AT);
        stubCommitment(c);
        var resp = service.buildResponse(deviation, principal).orElseThrow();
        assertEquals("DECLINED", resp.currentStage());
        assertEquals("completed", findStage(resp, "DECLINED").status());
    }

    @Test
    void expiredState_lastStageFailed() {
        var c = commitment(CommitmentState.EXPIRED, null, RESOLVED_AT);
        stubCommitment(c);
        var resp = service.buildResponse(deviation, principal).orElseThrow();
        assertEquals("EXPIRED", resp.currentStage());
        assertEquals("completed", resp.stages().get(0).status());
        assertTrue(resp.stages().stream().anyMatch(s -> "failed".equals(s.status())));
    }

    @Test
    void failedState_lastStageFailed() {
        var c = commitment(CommitmentState.FAILED, ACK_AT, RESOLVED_AT);
        stubCommitment(c);
        var resp = service.buildResponse(deviation, principal).orElseThrow();
        assertEquals("FAILED", resp.currentStage());
        assertEquals("completed", resp.stages().get(0).status());
        assertEquals("completed", resp.stages().get(1).status());
        assertTrue(resp.stages().stream().anyMatch(s -> "failed".equals(s.status())));
    }

    @Test
    void delegatedState() {
        var c = commitment(CommitmentState.DELEGATED, ACK_AT, RESOLVED_AT);
        stubCommitment(c);
        var resp = service.buildResponse(deviation, principal).orElseThrow();
        assertEquals("DELEGATED", resp.currentStage());
    }

    @Test
    void noCommitment_returnsEmpty() {
        when(commitmentReader.findByCorrelationId(DEV_ID.toString())).thenReturn(Optional.empty());
        var result = service.buildResponse(deviation, principal);
        assertTrue(result.isEmpty());
    }

    @Test
    void tenancyMismatch_returnsEmpty() {
        var c = commitment(CommitmentState.OPEN, null, null);
        when(commitmentReader.findByCorrelationId(DEV_ID.toString())).thenReturn(Optional.of(
                c.toBuilder().tenancyId("other-tenant").build()));
        var result = service.buildResponse(deviation, principal);
        assertTrue(result.isEmpty());
    }

    @Test
    void missingChannel_emptyMessages() {
        var c = commitment(CommitmentState.FULFILLED, ACK_AT, RESOLVED_AT);
        when(commitmentReader.findByCorrelationId(DEV_ID.toString())).thenReturn(Optional.of(c));
        when(channelService.findByName(deviation.piCommandChannelName)).thenReturn(Optional.empty());
        var resp = service.buildResponse(deviation, principal).orElseThrow();
        assertTrue(resp.messages().isEmpty());
    }

    @Test
    void messagesReturnedFromChannel() {
        var c = commitment(CommitmentState.OPEN, null, null);
        when(commitmentReader.findByCorrelationId(DEV_ID.toString())).thenReturn(Optional.of(c));
        when(channelService.findByName(deviation.piCommandChannelName))
                .thenReturn(Optional.of(stubChannel()));
        var msg = Message.builder()
                .id(1L).channelId(CHANNEL_ID).sender("clinical-service")
                .messageType(MessageType.COMMAND).actorType(ActorType.SYSTEM)
                .tenancyId(TENANT).content("{\"deviationId\":\"" + DEV_ID + "\"}")
                .createdAt(COMMANDED_AT).version(0).build();
        when(messageService.history(CHANNEL_ID, 0L, 100)).thenReturn(List.of(msg));
        var resp = service.buildResponse(deviation, principal).orElseThrow();
        assertEquals(1, resp.messages().size());
        assertEquals("clinical-service", resp.messages().get(0).sender());
    }

    private CommitmentLifecycleService.StageResponse findStage(
            CommitmentLifecycleService.CommitmentLifecycleResponse resp, String key) {
        return resp.stages().stream().filter(s -> key.equals(s.key())).findFirst().orElseThrow();
    }
}
