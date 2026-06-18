package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolAmendmentProposedEvent;
import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.entity.ProtocolAmendment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ProtocolAmendmentCaseServiceTest {

    @Mock ProtocolAmendmentCaseHub caseHub;
    @InjectMocks ProtocolAmendmentCaseService service;

    private ProtocolAmendmentProposedEvent event(UUID amendmentId) {
        return new ProtocolAmendmentProposedEvent(amendmentId, UUID.randomUUID(),
            "Dose escalation v2", "default");
    }

    private ProtocolAmendment amendment(UUID id, AmendmentCaseStatus status) {
        ProtocolAmendment a = new ProtocolAmendment();
        a.id = id;
        a.trialId = UUID.randomUUID();
        a.proposedChange = "Dose escalation v2";
        a.status = ProtocolAmendmentStatus.PROPOSED;
        a.amendmentCaseStatus = status;
        a.proposedAt = Instant.now();
        return a;
    }

    @Test
    void phase1_idempotency_guard_returns_null_when_not_NONE() {
        UUID id = UUID.randomUUID();
        ProtocolAmendment a = amendment(id, AmendmentCaseStatus.REQUESTED);
        Map<String, Object> ctx = service.prepareAndMark(event(id), a);
        assertThat(ctx).isNull();
        assertThat(a.amendmentCaseStatus).isEqualTo(AmendmentCaseStatus.REQUESTED);
    }

    @Test
    void phase1_sets_REQUESTED_on_NONE_status() {
        UUID id = UUID.randomUUID();
        ProtocolAmendment a = amendment(id, AmendmentCaseStatus.NONE);
        Map<String, Object> ctx = service.prepareAndMark(event(id), a);
        assertThat(a.amendmentCaseStatus).isEqualTo(AmendmentCaseStatus.REQUESTED);
        assertThat(ctx).isNotNull();
    }

    @Test
    void initial_context_contains_amendmentId_as_string() {
        UUID id = UUID.randomUUID();
        ProtocolAmendment a = amendment(id, AmendmentCaseStatus.NONE);
        Map<String, Object> ctx = service.prepareAndMark(event(id), a);
        assertThat(ctx.get("amendmentId")).isEqualTo(id.toString());
    }
}
