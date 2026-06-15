package io.casehub.clinical.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.ledger.RegulatorySubmissionLedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegulatorySubmissionLedgerWriterTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock Clock clock;
    @InjectMocks RegulatorySubmissionLedgerWriter writer;

    @Test
    void writes_entry_with_correct_fields() {
        Instant now = Instant.parse("2026-06-15T12:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(ledgerEntryRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());

        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = CtcaeGrade.GRADE_5;
        ae.tenantId = "test-tenant";

        writer.writeEntry(ae);

        verify(ledgerEntryRepository).save(
                argThat(entry -> {
                    RegulatorySubmissionLedgerEntry rsle = (RegulatorySubmissionLedgerEntry) entry;
                    return rsle.aeId.equals(ae.id)
                            && "GRADE_5".equals(rsle.grade)
                            && rsle.filedAt.equals(now)
                            && rsle.subjectId.equals(ae.enrollmentId)
                            && rsle.sequenceNumber == 1
                            && rsle.id != null;
                }),
                eq("default"));
    }
}
