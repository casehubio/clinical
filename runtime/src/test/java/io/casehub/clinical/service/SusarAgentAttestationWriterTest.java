package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalCapabilities;
import io.casehub.clinical.api.ClinicalTrustDimensions;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.engine.common.internal.event.ActionGateApprovedEvent;
import io.casehub.engine.common.internal.event.ActionGateExpiredEvent;
import io.casehub.engine.common.internal.event.ActionGateRejectedEvent;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.ledger.repository.CaseLedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SusarAgentAttestationWriterTest {
    @Mock EntityManager em;
    @Mock TypedQuery<AdverseEvent> query;
    @InjectMocks SusarAgentAttestationWriter writer;
    @Mock CaseLedgerEntryRepository caseLedgerEntryRepository;
    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock Clock clock;

    private static final Instant FIXED = Instant.parse("2026-06-16T12:00:00Z");

    private UUID susarCaseId;
    private UUID workerEntryId;

    @BeforeEach
    void setUp() {
        when(em.createNamedQuery("AdverseEvent.findBySusarOversightCaseId", AdverseEvent.class))
                .thenReturn(query);
        when(query.setParameter(eq("caseId"), any(UUID.class))).thenReturn(query);
        lenient().when(query.getResultStream()).thenReturn(Stream.empty());
        lenient().when(clock.instant()).thenReturn(FIXED);
        susarCaseId = UUID.randomUUID();
        workerEntryId = UUID.randomUUID();
        WorkerDecisionEntry entry = new WorkerDecisionEntry();
        entry.id = workerEntryId;
        entry.capabilityTag = ClinicalCapabilities.SAFETY_MONITORING;
        lenient().when(caseLedgerEntryRepository.findWorkerDecisionsByCaseId(susarCaseId))
                .thenReturn(List.of(entry));
    }

    @Test
    void approved_gate_writes_endorsed_with_human_attestor() {
        persistAe(susarCaseId);
        writer.onApproved(new ActionGateApprovedEvent(susarCaseId, "default", 1L, null, "dr-smith", null));
        verify(ledgerEntryRepository).saveAttestation(
                argThat(a ->
                        a.ledgerEntryId.equals(workerEntryId)
                        && a.subjectId.equals(susarCaseId)
                        && a.verdict == AttestationVerdict.ENDORSED
                        && a.attestorType == ActorType.HUMAN
                        && "dr-smith".equals(a.attestorId)
                        && ClinicalCapabilities.SAFETY_MONITORING.equals(a.capabilityTag)
                        && ClinicalTrustDimensions.SAFETY_ACCURACY.equals(a.trustDimension)
                        && a.confidence == 1.0
                        && FIXED.equals(a.occurredAt)),
                eq("default"));
    }

    @Test
    void rejected_gate_writes_challenged_with_human_attestor() {
        persistAe(susarCaseId);
        writer.onRejected(new ActionGateRejectedEvent(susarCaseId, "default", 1L, null, "dr-jones"));
        verify(ledgerEntryRepository).saveAttestation(
                argThat(a ->
                        a.verdict == AttestationVerdict.CHALLENGED
                        && a.attestorType == ActorType.HUMAN
                        && "dr-jones".equals(a.attestorId)),
                eq("default"));
    }

    @Test
    void expired_gate_writes_challenged_with_system_attestor() {
        persistAe(susarCaseId);
        writer.onExpired(new ActionGateExpiredEvent(susarCaseId, "default", 1L));
        verify(ledgerEntryRepository).saveAttestation(
                argThat(a ->
                        a.verdict == AttestationVerdict.CHALLENGED
                        && a.attestorType == ActorType.SYSTEM),
                eq("default"));
    }

    @Test
    void non_susar_case_id_silently_skips_attestation() {
        writer.onApproved(new ActionGateApprovedEvent(UUID.randomUUID(), "default", 1L, null, "dr-smith", null));
        verify(ledgerEntryRepository, never()).saveAttestation(any(), any());
    }

    @Test
    void missing_worker_decision_entry_skips_attestation() {
        UUID caseId = UUID.randomUUID();
        persistAe(caseId);
        when(caseLedgerEntryRepository.findWorkerDecisionsByCaseId(caseId))
                .thenReturn(List.of());
        writer.onApproved(new ActionGateApprovedEvent(caseId, "default", 1L, null, "dr-smith", null));
        verify(ledgerEntryRepository, never()).saveAttestation(any(), any());
    }

    void persistAe(UUID susarOversightCaseId) {
        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = CtcaeGrade.GRADE_4;
        ae.unexpected = true;
        ae.suspected = true;
        ae.actuality = EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.tenantId = "test-tenant";
        ae.susarOversightStatus = SusarOversightStatus.REQUESTED;
        ae.susarOversightCaseId = susarOversightCaseId;
        when(query.getResultStream()).thenReturn(Stream.of(ae));
    }
}
