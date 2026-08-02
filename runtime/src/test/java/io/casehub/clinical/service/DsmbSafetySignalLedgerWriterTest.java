package io.casehub.clinical.service;

import io.casehub.clinical.api.DsmbSafetySignalEvent;
import io.casehub.clinical.ledger.DsmbSafetySignalLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DsmbSafetySignalLedgerWriterTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock Clock clock;
    @InjectMocks DsmbSafetySignalLedgerWriter writer;

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-30T12:00:00Z");
    private UUID trialId;
    private UUID siteA, siteB, siteC;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        when(ledgerEntryRepository.save(any(), any())).thenAnswer(i -> i.getArgument(0));
        when(ledgerEntryRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());

        trialId = UUID.randomUUID();
        siteA = UUID.randomUUID();
        siteB = UUID.randomUUID();
        siteC = UUID.randomUUID();
    }

    @Test
    void onSignalDetected_setsCorrectFields() {
        DsmbSafetySignalEvent event = new DsmbSafetySignalEvent(
            trialId, "GRADE_THRESHOLD", List.of(siteA, siteB, siteC),
            "3 of 5 sites show Grade 3+ AE rate above 10%", "default");

        writer.onSignalDetected(event);

        DsmbSafetySignalLedgerEntry entry = captureEntry();
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.EVENT);
        assertThat(entry.actorId).isEqualTo("clinical-service");
        assertThat(entry.actorType).isEqualTo(ActorType.SYSTEM);
        assertThat(entry.actorRole).isEqualTo("TrialSafetyAggregation");
        assertThat(entry.occurredAt).isEqualTo(FIXED_INSTANT);
        assertThat(entry.subjectId).isEqualTo(trialId);
        assertThat(entry.trialId).isEqualTo(trialId);
        assertThat(entry.signalType).isEqualTo("GRADE_THRESHOLD");
        assertThat(entry.affectedSiteCount).isEqualTo(3);
        assertThat(entry.summary).contains("Grade 3+");
        assertThat(entry.id).isNotNull();
    }

    @Test
    void onSignalDetected_sequenceNumber1WhenNoPriorEntries() {
        DsmbSafetySignalEvent event = new DsmbSafetySignalEvent(
            trialId, "CROSS_SITE_CLUSTER", List.of(siteA, siteB, siteC),
            "hepatotoxicity at 3 sites", "default");

        writer.onSignalDetected(event);

        assertThat(captureEntry().sequenceNumber).isEqualTo(1);
    }

    @Test
    void onSignalDetected_sequenceNumberIncrementsFromPrior() {
        LedgerEntry prior = new DsmbSafetySignalLedgerEntry();
        prior.sequenceNumber = 4;
        when(ledgerEntryRepository.findLatestBySubjectId(eq(trialId), any())).thenReturn(Optional.of(prior));

        DsmbSafetySignalEvent event = new DsmbSafetySignalEvent(
            trialId, "GRADE_THRESHOLD", List.of(siteA),
            "summary", "default");

        writer.onSignalDetected(event);

        assertThat(captureEntry().sequenceNumber).isEqualTo(5);
    }

    private DsmbSafetySignalLedgerEntry captureEntry() {
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), any());
        return (DsmbSafetySignalLedgerEntry) captor.getValue();
    }
}
