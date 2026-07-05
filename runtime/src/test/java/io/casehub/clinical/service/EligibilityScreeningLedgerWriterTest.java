package io.casehub.clinical.service;

import io.casehub.clinical.api.model.CriterionResult;
import io.casehub.clinical.api.model.EligibilityScreeningResult;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.ledger.EligibilityScreeningLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EligibilityScreeningLedgerWriterTest {

    @Mock LedgerEntryRepository repo;
    @Mock Clock clock;
    @InjectMocks EligibilityScreeningLedgerWriter writer;

    private PatientEnrollment enrollment(UUID id) {
        PatientEnrollment e = new PatientEnrollment();
        e.id = id;
        e.tenantId = "default";
        return e;
    }

    @Test
    void writeScreeningEntry_sets_criteriaCount_to_total_list_size() {
        UUID id = UUID.randomUUID();
        when(clock.instant()).thenReturn(Instant.now());
        when(repo.findLatestBySubjectId(id, "default")).thenReturn(Optional.empty());

        var criteria = List.of(
            new CriterionResult("c1", false, true),
            new CriterionResult("c2", true, false)
        );
        writer.writeScreeningEntry(enrollment(id), criteria, EligibilityScreeningResult.MARGINAL);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(repo).save(captor.capture(), eq("default"));
        assertThat(((EligibilityScreeningLedgerEntry) captor.getValue()).criteriaCount).isEqualTo(2);
    }

    @Test
    void writeScreeningEntry_sets_marginalCount_to_count_of_marginal_true() {
        UUID id = UUID.randomUUID();
        when(clock.instant()).thenReturn(Instant.now());
        when(repo.findLatestBySubjectId(id, "default")).thenReturn(Optional.empty());

        var criteria = List.of(
            new CriterionResult("c1", false, true),   // marginal
            new CriterionResult("c2", true, false),   // met
            new CriterionResult("c3", false, false)   // excluded
        );
        writer.writeScreeningEntry(enrollment(id), criteria, EligibilityScreeningResult.MARGINAL);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(repo).save(captor.capture(), eq("default"));
        assertThat(((EligibilityScreeningLedgerEntry) captor.getValue()).marginalCount).isEqualTo(1);
    }

    @Test
    void writeScreeningEntry_uses_correct_screeningResult() {
        UUID id = UUID.randomUUID();
        when(clock.instant()).thenReturn(Instant.now());
        when(repo.findLatestBySubjectId(id, "default")).thenReturn(Optional.empty());

        var criteria = List.of(new CriterionResult("c1", false, true));
        writer.writeScreeningEntry(enrollment(id), criteria, EligibilityScreeningResult.MARGINAL);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(repo).save(captor.capture(), eq("default"));
        assertThat(((EligibilityScreeningLedgerEntry) captor.getValue()).screeningResult)
            .isEqualTo("MARGINAL");
    }
}
