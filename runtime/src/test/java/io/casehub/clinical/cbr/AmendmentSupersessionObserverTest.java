package io.casehub.clinical.cbr;

import io.casehub.clinical.api.ProtocolAmendmentResolvedEvent;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class AmendmentSupersessionObserverTest {

    private CbrCaseMemoryStore store;
    private AmendmentSupersessionObserver observer;
    private UUID trialId;

    @BeforeEach
    void setup() {
        store = mock(CbrCaseMemoryStore.class);
        observer = new AmendmentSupersessionObserver(store);
        trialId = UUID.randomUUID();
    }

    @Test
    void supersedesPriorAmendmentCbrCase() {
        UUID priorId = UUID.randomUUID();
        UUID currentId = UUID.randomUUID();

        ProtocolAmendment prior = new ProtocolAmendment();
        prior.id = priorId;
        prior.trialId = trialId;
        prior.proposedAt = Instant.parse("2026-01-01T00:00:00Z");
        prior.tenantId = "tenant-1";

        ProtocolAmendment current = new ProtocolAmendment();
        current.id = currentId;
        current.trialId = trialId;
        current.proposedAt = Instant.parse("2026-06-01T00:00:00Z");
        current.tenantId = "tenant-1";

        var event = new ProtocolAmendmentResolvedEvent(
            currentId, trialId, ProtocolAmendmentStatus.APPROVED,
            AmendmentRecommendation.PROCEED, "tenant-1");

        observer.onAmendmentResolved(event, List.of(prior, current));

        verify(store).supersede(
            priorId.toString(),
            "tenant-1",
            currentId.toString(),
            "Superseded by newer amendment " + currentId);
    }

    @Test
    void noSupersessionWhenOnlyOneAmendment() {
        UUID currentId = UUID.randomUUID();

        ProtocolAmendment current = new ProtocolAmendment();
        current.id = currentId;
        current.trialId = trialId;
        current.proposedAt = Instant.parse("2026-06-01T00:00:00Z");
        current.tenantId = "tenant-1";

        var event = new ProtocolAmendmentResolvedEvent(
            currentId, trialId, ProtocolAmendmentStatus.APPROVED,
            AmendmentRecommendation.PROCEED, "tenant-1");

        observer.onAmendmentResolved(event, List.of(current));

        verifyNoInteractions(store);
    }

    @Test
    void supersedesMostRecentPriorAmendment() {
        UUID oldestId = UUID.randomUUID();
        UUID middleId = UUID.randomUUID();
        UUID currentId = UUID.randomUUID();

        ProtocolAmendment oldest = new ProtocolAmendment();
        oldest.id = oldestId;
        oldest.trialId = trialId;
        oldest.proposedAt = Instant.parse("2025-01-01T00:00:00Z");
        oldest.tenantId = "tenant-1";

        ProtocolAmendment middle = new ProtocolAmendment();
        middle.id = middleId;
        middle.trialId = trialId;
        middle.proposedAt = Instant.parse("2026-01-01T00:00:00Z");
        middle.tenantId = "tenant-1";

        ProtocolAmendment current = new ProtocolAmendment();
        current.id = currentId;
        current.trialId = trialId;
        current.proposedAt = Instant.parse("2026-06-01T00:00:00Z");
        current.tenantId = "tenant-1";

        var event = new ProtocolAmendmentResolvedEvent(
            currentId, trialId, ProtocolAmendmentStatus.APPROVED,
            AmendmentRecommendation.PROCEED, "tenant-1");

        observer.onAmendmentResolved(event, List.of(oldest, middle, current));

        verify(store).supersede(
            middleId.toString(),
            "tenant-1",
            currentId.toString(),
            "Superseded by newer amendment " + currentId);
        verifyNoMoreInteractions(store);
    }

    @Test
    void noSupersessionWhenAmendmentListEmpty() {
        UUID currentId = UUID.randomUUID();

        var event = new ProtocolAmendmentResolvedEvent(
            currentId, trialId, ProtocolAmendmentStatus.APPROVED,
            AmendmentRecommendation.PROCEED, "tenant-1");

        observer.onAmendmentResolved(event, List.of());

        verifyNoInteractions(store);
    }
}
