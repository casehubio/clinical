package io.casehub.clinical.service;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.ledger.AeGradeChangeLedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AeGradeChangeLedgerWriterTest {

    private LedgerEntryRepository repo;
    private AeGradeChangeLedgerWriter writer;

    @BeforeEach
    void setUp() {
        repo = mock(LedgerEntryRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-21T12:00:00Z"), ZoneOffset.UTC);
        when(repo.findLatestBySubjectId(any(), eq("default"))).thenReturn(Optional.empty());
        writer = new AeGradeChangeLedgerWriter();
        writer.ledgerEntryRepository = repo;
        writer.clock = clock;
    }

    @Test
    void writeGradeChangeEntry_writesCorrectFields() {
        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = CtcaeGrade.GRADE_3;

        writer.writeGradeChangeEntry(ae, CtcaeGrade.GRADE_1, "Patient condition worsened");

        var captor = ArgumentCaptor.forClass(AeGradeChangeLedgerEntry.class);
        verify(repo).save(captor.capture(), eq("default"));
        var entry = captor.getValue();
        assertEquals("GRADE_1", entry.previousGrade);
        assertEquals("GRADE_3", entry.newGrade);
        assertEquals("Patient condition worsened", entry.reason);
        assertNotNull(entry.id);
        assertEquals(1, entry.sequenceNumber);
        assertNotNull(entry.supplements);
        assertFalse(entry.supplements.isEmpty());
    }

    @Test
    void writeGradeChangeEntry_nullPreviousGrade_storedAsNull() {
        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = CtcaeGrade.GRADE_2;

        writer.writeGradeChangeEntry(ae, null, "Initial report");

        var captor = ArgumentCaptor.forClass(AeGradeChangeLedgerEntry.class);
        verify(repo).save(captor.capture(), eq("default"));
        assertNull(captor.getValue().previousGrade);
    }

    @Test
    void writeGradeChangeEntry_sequenceNumberIncrements() {
        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = CtcaeGrade.GRADE_3;

        var existing = new AeGradeChangeLedgerEntry();
        existing.sequenceNumber = 3;
        when(repo.findLatestBySubjectId(ae.id, "default")).thenReturn(Optional.of(existing));

        writer.writeGradeChangeEntry(ae, CtcaeGrade.GRADE_1, "reason");

        var captor = ArgumentCaptor.forClass(AeGradeChangeLedgerEntry.class);
        verify(repo).save(captor.capture(), eq("default"));
        assertEquals(4, captor.getValue().sequenceNumber);
    }
}
