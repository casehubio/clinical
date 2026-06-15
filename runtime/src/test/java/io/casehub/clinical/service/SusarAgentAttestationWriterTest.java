package io.casehub.clinical.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.ledger.repository.CaseLedgerEntryRepository;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SusarAgentAttestationWriterTest {

    @Inject SusarAgentAttestationWriter writer;
    @InjectMock CaseLedgerEntryRepository caseLedgerEntryRepository;
    @InjectMock LedgerEntryRepository ledgerEntryRepository;

    private UUID susarCaseId;
    private UUID workerEntryId;

    @BeforeEach
    void setUp() {
        reset(caseLedgerEntryRepository, ledgerEntryRepository);
        susarCaseId = UUID.randomUUID();
        workerEntryId = UUID.randomUUID();
        WorkerDecisionEntry entry = new WorkerDecisionEntry();
        entry.id = workerEntryId;
        entry.capabilityTag = ClinicalCapabilities.SAFETY_MONITORING;
        when(caseLedgerEntryRepository.findWorkerDecisionsByCaseId(susarCaseId))
                .thenReturn(List.of(entry));
    }

    @Test
    @Transactional
    void approved_gate_writes_endorsed_with_human_attestor() {
        persistAe(susarCaseId);
        writer.onApproved(new ActionGateApprovedEvent(susarCaseId, 1L, null, "dr-smith"));
        verify(ledgerEntryRepository).saveAttestation(
                argThat(a ->
                        a.ledgerEntryId.equals(workerEntryId)
                        && a.subjectId.equals(susarCaseId)
                        && a.verdict == AttestationVerdict.ENDORSED
                        && a.attestorType == ActorType.HUMAN
                        && "dr-smith".equals(a.attestorId)
                        && ClinicalCapabilities.SAFETY_MONITORING.equals(a.capabilityTag)
                        && ClinicalTrustDimensions.SAFETY_ACCURACY.equals(a.trustDimension)
                        && a.confidence == 1.0),
                eq("test-tenant"));
    }

    @Test
    @Transactional
    void rejected_gate_writes_challenged_with_human_attestor() {
        persistAe(susarCaseId);
        writer.onRejected(new ActionGateRejectedEvent(susarCaseId, 1L, null, "dr-jones"));
        verify(ledgerEntryRepository).saveAttestation(
                argThat(a ->
                        a.verdict == AttestationVerdict.CHALLENGED
                        && a.attestorType == ActorType.HUMAN
                        && "dr-jones".equals(a.attestorId)),
                eq("test-tenant"));
    }

    @Test
    @Transactional
    void expired_gate_writes_challenged_with_system_attestor() {
        persistAe(susarCaseId);
        writer.onExpired(new ActionGateExpiredEvent(susarCaseId, 1L));
        verify(ledgerEntryRepository).saveAttestation(
                argThat(a ->
                        a.verdict == AttestationVerdict.CHALLENGED
                        && a.attestorType == ActorType.SYSTEM),
                eq("test-tenant"));
    }

    @Test
    @Transactional
    void non_susar_case_id_silently_skips_attestation() {
        writer.onApproved(new ActionGateApprovedEvent(UUID.randomUUID(), 1L, null, "dr-smith"));
        verify(ledgerEntryRepository, never()).saveAttestation(any(), any());
    }

    @Test
    @Transactional
    void missing_worker_decision_entry_skips_attestation() {
        UUID caseId = UUID.randomUUID();
        persistAe(caseId);
        when(caseLedgerEntryRepository.findWorkerDecisionsByCaseId(caseId))
                .thenReturn(List.of());
        writer.onApproved(new ActionGateApprovedEvent(caseId, 1L, null, "dr-smith"));
        verify(ledgerEntryRepository, never()).saveAttestation(any(), any());
    }

    @Transactional
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
        ae.persist();
    }
}
