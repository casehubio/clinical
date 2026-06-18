package io.casehub.clinical.service;

import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.clinical.ledger.ProtocolAmendmentLedgerEntry;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProtocolAmendmentLedgerWriterTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock Clock clock;
    @InjectMocks ProtocolAmendmentLedgerWriter writer;

    private ProtocolAmendment amendment(UUID id) {
        ProtocolAmendment a = new ProtocolAmendment();
        a.id = id;
        a.trialId = UUID.randomUUID();
        a.proposedChange = "Dose escalation";
        a.status = ProtocolAmendmentStatus.PROPOSED;
        a.amendmentCaseStatus = AmendmentCaseStatus.NONE;
        a.tenantId = "default";
        return a;
    }

    @Test
    void writeProposalEntry_includes_proposedChange() {
        UUID id = UUID.randomUUID();
        when(clock.instant()).thenReturn(Instant.now());
        when(ledgerEntryRepository.findLatestBySubjectId(id, "default")).thenReturn(Optional.empty());

        writer.writeProposalEntry(amendment(id));

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), eq("default"));
        assertThat(((ProtocolAmendmentLedgerEntry) captor.getValue()).proposedChange)
            .isEqualTo("Dose escalation");
    }

    @Test
    void writeResolutionEntry_includes_supervisorRecommendation() {
        UUID id = UUID.randomUUID();
        ProtocolAmendment a = amendment(id);
        a.supervisorRecommendation = AmendmentRecommendation.PROCEED;
        a.status = ProtocolAmendmentStatus.APPROVED;
        when(clock.instant()).thenReturn(Instant.now());
        when(ledgerEntryRepository.findLatestBySubjectId(id, "default")).thenReturn(Optional.empty());

        writer.writeResolutionEntry(a);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), eq("default"));
        ProtocolAmendmentLedgerEntry entry = (ProtocolAmendmentLedgerEntry) captor.getValue();
        assertThat(entry.supervisorRecommendation).isEqualTo("PROCEED");
        assertThat(entry.status).isEqualTo("APPROVED");
    }

    @Test
    void sequenceNumber_increments_between_proposal_and_resolution_entries() {
        UUID id = UUID.randomUUID();
        when(clock.instant()).thenReturn(Instant.now());
        // Proposal: no prior entry → sequenceNumber = 1
        when(ledgerEntryRepository.findLatestBySubjectId(id, "default")).thenReturn(Optional.empty());
        writer.writeProposalEntry(amendment(id));

        // Resolution: prior entry has sequenceNumber=1 → resolution gets 2
        LedgerEntry prior = new ProtocolAmendmentLedgerEntry();
        prior.sequenceNumber = 1;
        when(ledgerEntryRepository.findLatestBySubjectId(id, "default")).thenReturn(Optional.of(prior));

        ProtocolAmendment a = amendment(id);
        a.supervisorRecommendation = AmendmentRecommendation.PROCEED;
        a.status = ProtocolAmendmentStatus.APPROVED;
        writer.writeResolutionEntry(a);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(captor.capture(), eq("default"));
        assertThat(captor.getAllValues().get(0).sequenceNumber).isEqualTo(1);
        assertThat(captor.getAllValues().get(1).sequenceNumber).isEqualTo(2);
    }
}
