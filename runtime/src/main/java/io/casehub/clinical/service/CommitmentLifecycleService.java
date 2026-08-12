package io.casehub.clinical.service;

import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.MessageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CommitmentLifecycleService {

    @Inject CommitmentReader commitmentReader;
    @Inject ChannelService channelService;
    @Inject MessageService messageService;

    public record CommitmentLifecycleResponse(
            String id,
            String currentStage,
            List<StageResponse> stages,
            List<ChannelMessageResponse> messages
    ) {}

    public record StageResponse(String key, String actor, String timestamp, String status) {}

    public record ChannelMessageResponse(String sender, String content, String timestamp) {}

    public Optional<CommitmentLifecycleResponse> buildResponse(ProtocolDeviation deviation, CurrentPrincipal principal) {
        Optional<Commitment> opt = commitmentReader.findByCorrelationId(deviation.id.toString());
        if (opt.isEmpty()) return Optional.empty();

        Commitment commitment = opt.get();
        if (!principal.tenancyId().equals(commitment.tenancyId())) return Optional.empty();

        List<StageResponse> stages = deriveStages(commitment, deviation);
        String currentStage = deriveCurrentStage(commitment);
        List<ChannelMessageResponse> messages = fetchMessages(deviation);

        return Optional.of(new CommitmentLifecycleResponse(
                commitment.id().toString(), currentStage, stages, messages));
    }

    private String deriveCurrentStage(Commitment commitment) {
        return switch (commitment.state()) {
            case OPEN -> "COMMANDED";
            case ACKNOWLEDGED -> "ACKNOWLEDGED";
            case FULFILLED -> "DONE";
            case DECLINED -> "DECLINED";
            case FAILED -> "FAILED";
            case DELEGATED -> "DELEGATED";
            case EXPIRED -> "EXPIRED";
        };
    }

    private List<StageResponse> deriveStages(Commitment commitment, ProtocolDeviation deviation) {
        CommitmentState state = commitment.state();
        String requester = commitment.requester();
        String obligor = commitment.obligor();
        boolean isTerminalFailure = state == CommitmentState.FAILED || state == CommitmentState.EXPIRED;
        boolean reachedAck = commitment.acknowledgedAt() != null;
        boolean reachedTerminal = state.isTerminal();

        List<StageResponse> stages = new ArrayList<>(4);

        stages.add(new StageResponse("COMMANDED", requester,
                deviation.commandedAt != null ? deviation.commandedAt.toString() : null, "completed"));

        if (reachedAck) {
            String ackStatus = (state == CommitmentState.ACKNOWLEDGED) ? "active" : "completed";
            stages.add(new StageResponse("ACKNOWLEDGED", obligor,
                    commitment.acknowledgedAt().toString(), ackStatus));
        } else if (reachedTerminal) {
            stages.add(new StageResponse("ACKNOWLEDGED", null, null, "completed"));
        } else {
            stages.add(new StageResponse("ACKNOWLEDGED", null, null, "pending"));
        }

        if (state == CommitmentState.FULFILLED) {
            stages.add(new StageResponse("DONE", obligor,
                    commitment.resolvedAt() != null ? commitment.resolvedAt().toString() : null, "completed"));
        } else if (isTerminalFailure && !reachedAck) {
            stages.add(new StageResponse("DONE", null, null, "failed"));
        } else if (isTerminalFailure) {
            stages.add(new StageResponse("DONE", null,
                    commitment.resolvedAt() != null ? commitment.resolvedAt().toString() : null, "failed"));
        } else {
            stages.add(new StageResponse("DONE", null, null, "pending"));
        }

        if (state == CommitmentState.DECLINED) {
            stages.add(new StageResponse("DECLINED", obligor,
                    commitment.resolvedAt() != null ? commitment.resolvedAt().toString() : null, "completed"));
        } else {
            stages.add(new StageResponse("DECLINED", null, null, "pending"));
        }

        return stages;
    }

    private List<ChannelMessageResponse> fetchMessages(ProtocolDeviation deviation) {
        if (deviation.piCommandChannelName == null) return List.of();
        return channelService.findByName(deviation.piCommandChannelName)
                .map(channel -> messageService.history(channel.id(), 0L, 100).stream()
                        .map(m -> new ChannelMessageResponse(
                                m.sender(), m.content(),
                                m.createdAt() != null ? m.createdAt().toString() : null))
                        .toList())
                .orElse(List.of());
    }
}
