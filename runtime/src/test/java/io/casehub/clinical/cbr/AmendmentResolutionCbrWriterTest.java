package io.casehub.clinical.cbr;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.casehub.clinical.api.ProtocolAmendmentResolvedEvent;
import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.neocortex.memory.cbr.TextualCbrCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class AmendmentResolutionCbrWriterTest {

    @Inject AmendmentResolutionCbrWriter writer;
    @InjectMock ClinicalCbrService cbrService;
    @InjectMock ClinicalScopeResolver scopeResolver;

    UUID amendmentId;
    UUID trialId;
    UUID engineCaseId;

    @BeforeEach
    @Transactional
    void setup() {
        amendmentId = UUID.randomUUID();
        trialId = UUID.randomUUID();
        engineCaseId = UUID.randomUUID();

        ProtocolAmendment amendment = new ProtocolAmendment();
        amendment.id = amendmentId;
        amendment.trialId = trialId;
        amendment.engineCaseId = engineCaseId;
        amendment.proposedChange = "Increase dose by 20%";
        amendment.status = ProtocolAmendmentStatus.APPROVED;
        amendment.supervisorRecommendation = AmendmentRecommendation.PROCEED;
        amendment.amendmentCaseStatus = AmendmentCaseStatus.COMPLETED;
        amendment.tenantId = "default";
        amendment.proposedAt = Instant.now();
        amendment.persist();

        when(scopeResolver.forAmendment(any())).thenReturn(java.util.Optional.of(io.casehub.platform.api.path.Path.of("trial-1")));
        when(cbrService.storeIdempotent(any(), anyString(), anyString(), any(), anyString(), anyString(), any()))
            .thenReturn("cbr-case-id-123");
    }

    @Test
    void stores_textual_cbr_case_with_proceed_recommendation() {
        var event = new ProtocolAmendmentResolvedEvent(
            amendmentId, trialId,
            ProtocolAmendmentStatus.APPROVED,
            AmendmentRecommendation.PROCEED,
            "default"
        );

        writer.onAmendmentResolved(event);

        ArgumentCaptor<TextualCbrCase> caseCaptor = ArgumentCaptor.forClass(TextualCbrCase.class);
        verify(cbrService).storeIdempotent(
            caseCaptor.capture(),
            eq("clinical-amendment"),
            eq(amendmentId.toString()),
            eq(ClinicalCbrDomains.AMENDMENT),
            eq("default"),
            eq(engineCaseId.toString()),
            any()
        );

        TextualCbrCase stored = caseCaptor.getValue();
        assertThat(stored.problem()).isEqualTo("Increase dose by 20%");
        assertThat(stored.solution()).isEqualTo("PROCEED");
        assertThat(stored.outcome()).isEqualTo("APPROVED");
        assertThat(stored.confidence()).isEqualTo(1.0);
    }

    @Test
    void stores_halt_recommendation() {
        updateAmendmentStatus(ProtocolAmendmentStatus.HALTED, AmendmentRecommendation.HALT);

        var event = new ProtocolAmendmentResolvedEvent(
            amendmentId, trialId,
            ProtocolAmendmentStatus.HALTED,
            AmendmentRecommendation.HALT,
            "default"
        );

        writer.onAmendmentResolved(event);

        ArgumentCaptor<TextualCbrCase> caseCaptor = ArgumentCaptor.forClass(TextualCbrCase.class);
        verify(cbrService).storeIdempotent(
            caseCaptor.capture(),
            anyString(), anyString(), any(), anyString(), anyString(), any()
        );

        TextualCbrCase stored = caseCaptor.getValue();
        assertThat(stored.solution()).isEqualTo("HALT");
        assertThat(stored.outcome()).isEqualTo("HALTED");
    }

    @Test
    void stores_refer_to_dsmb_recommendation() {
        updateAmendmentStatus(ProtocolAmendmentStatus.SUPERVISED, AmendmentRecommendation.REFER_TO_DSMB);

        var event = new ProtocolAmendmentResolvedEvent(
            amendmentId, trialId,
            ProtocolAmendmentStatus.SUPERVISED,
            AmendmentRecommendation.REFER_TO_DSMB,
            "default"
        );

        writer.onAmendmentResolved(event);

        ArgumentCaptor<TextualCbrCase> caseCaptor = ArgumentCaptor.forClass(TextualCbrCase.class);
        verify(cbrService).storeIdempotent(
            caseCaptor.capture(),
            anyString(), anyString(), any(), anyString(), anyString(), any()
        );

        TextualCbrCase stored = caseCaptor.getValue();
        assertThat(stored.solution()).isEqualTo("REFER_TO_DSMB");
        assertThat(stored.outcome()).isEqualTo("SUPERVISED");
    }

    @Test
    void handles_null_recommendation_gracefully() {
        var event = new ProtocolAmendmentResolvedEvent(
            amendmentId, trialId,
            ProtocolAmendmentStatus.APPROVED,
            null,
            "default"
        );

        writer.onAmendmentResolved(event);

        ArgumentCaptor<TextualCbrCase> caseCaptor = ArgumentCaptor.forClass(TextualCbrCase.class);
        verify(cbrService).storeIdempotent(
            caseCaptor.capture(),
            anyString(), anyString(), any(), anyString(), anyString(), any()
        );

        TextualCbrCase stored = caseCaptor.getValue();
        assertThat(stored.solution()).isEqualTo("UNKNOWN");
    }

    @Test
    void handles_null_engineCaseId() {
        updateAmendmentEngineCase(null);

        var event = new ProtocolAmendmentResolvedEvent(
            amendmentId, trialId,
            ProtocolAmendmentStatus.APPROVED,
            AmendmentRecommendation.PROCEED,
            "default"
        );

        writer.onAmendmentResolved(event);

        verify(cbrService).storeIdempotent(
            any(TextualCbrCase.class),
            eq("clinical-amendment"),
            eq(amendmentId.toString()),
            eq(ClinicalCbrDomains.AMENDMENT),
            eq("default"),
            isNull(),
            any()
        );
    }

    @Test
    void returns_silently_if_amendment_not_found() {
        var event = new ProtocolAmendmentResolvedEvent(
            UUID.randomUUID(), trialId,
            ProtocolAmendmentStatus.APPROVED,
            AmendmentRecommendation.PROCEED,
            "default"
        );

        writer.onAmendmentResolved(event);

        verifyNoInteractions(cbrService);
    }

    @Transactional
    void updateAmendmentStatus(ProtocolAmendmentStatus status, AmendmentRecommendation recommendation) {
        ProtocolAmendment a = ProtocolAmendment.findById(amendmentId);
        a.status = status;
        a.supervisorRecommendation = recommendation;
    }

    @Transactional
    void updateAmendmentEngineCase(UUID caseId) {
        ProtocolAmendment a = ProtocolAmendment.findById(amendmentId);
        a.engineCaseId = caseId;
    }
}
