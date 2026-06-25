package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProtocolAmendmentStatusUpdaterTest {

    @Inject ProtocolAmendmentStatusUpdater updater;
    @InjectMock ProtocolAmendmentLedgerWriter ledgerWriter;

    UUID amendmentId;

    @BeforeEach
    @Transactional
    void setup() {
        amendmentId = UUID.randomUUID();
        ProtocolAmendment a = new ProtocolAmendment();
        a.id = amendmentId;
        a.trialId = UUID.randomUUID();
        a.proposedChange = "Dose escalation";
        a.status = ProtocolAmendmentStatus.PROPOSED;
        a.amendmentCaseStatus = AmendmentCaseStatus.REQUESTED;
        a.tenantId = "default";
        a.proposedAt = Instant.now();
        a.persist();
    }

    @Test
    void proceed_sets_APPROVED_COMPLETED_and_writes_ledger() {
        updater.applyRecommendation(amendmentId, "PROCEED");

        ProtocolAmendment a = findAmendment(amendmentId);
        assertThat(a.supervisorRecommendation).isEqualTo(AmendmentRecommendation.PROCEED);
        assertThat(a.status).isEqualTo(ProtocolAmendmentStatus.APPROVED);
        assertThat(a.amendmentCaseStatus).isEqualTo(AmendmentCaseStatus.COMPLETED);
        verify(ledgerWriter).writeResolutionEntry(any());
    }

    @Test
    void halt_sets_HALTED_COMPLETED() {
        updater.applyRecommendation(amendmentId, "HALT");

        ProtocolAmendment a = findAmendment(amendmentId);
        assertThat(a.supervisorRecommendation).isEqualTo(AmendmentRecommendation.HALT);
        assertThat(a.status).isEqualTo(ProtocolAmendmentStatus.HALTED);
        assertThat(a.amendmentCaseStatus).isEqualTo(AmendmentCaseStatus.COMPLETED);
    }

    @Test
    void refer_to_dsmb_sets_SUPERVISED_COMPLETED() {
        updater.applyRecommendation(amendmentId, "REFER_TO_DSMB");

        ProtocolAmendment a = findAmendment(amendmentId);
        assertThat(a.supervisorRecommendation).isEqualTo(AmendmentRecommendation.REFER_TO_DSMB);
        assertThat(a.status).isEqualTo(ProtocolAmendmentStatus.SUPERVISED);
        assertThat(a.amendmentCaseStatus).isEqualTo(AmendmentCaseStatus.COMPLETED);
    }

    @Test
    void unknown_recommendation_sets_FAILED_and_leaves_supervisorRecommendation_null() {
        updater.applyRecommendation(amendmentId, "UNKNOWN_VALUE");

        ProtocolAmendment a = findAmendment(amendmentId);
        assertThat(a.supervisorRecommendation).isNull();
        assertThat(a.amendmentCaseStatus).isEqualTo(AmendmentCaseStatus.FAILED);
        verifyNoInteractions(ledgerWriter);
    }

    @Test
    void idempotent_when_supervisorRecommendation_already_set() {
        updater.applyRecommendation(amendmentId, "PROCEED");
        reset(ledgerWriter);

        updater.applyRecommendation(amendmentId, "HALT");

        ProtocolAmendment a = findAmendment(amendmentId);
        assertThat(a.supervisorRecommendation).isEqualTo(AmendmentRecommendation.PROCEED);
        verifyNoInteractions(ledgerWriter);
    }

    @Test
    void unknown_amendmentId_returns_silently() {
        updater.applyRecommendation(UUID.randomUUID(), "PROCEED");
        verifyNoInteractions(ledgerWriter);
    }

    @Transactional
    ProtocolAmendment findAmendment(UUID id) {
        return ProtocolAmendment.findById(id);
    }
}
