package io.casehub.clinical.report;

import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IndSafetyReportServiceTest {

    private EntityManager em;
    private LedgerEntryRepository ledgerRepo;
    private IndSafetyReportService service;

    private final UUID trialId = UUID.randomUUID();
    private final String tenancyId = "default";
    private final Instant from = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant to = Instant.parse("2026-06-30T23:59:59Z");

    @BeforeEach
    void setup() {
        em = mock(EntityManager.class);
        ledgerRepo = mock(LedgerEntryRepository.class);
        service = new IndSafetyReportService(em, ledgerRepo);
    }

    @Test
    void filtersIcsrsByGrade3OrHigher() {
        var aes = List.of(
                ae(CtcaeGrade.GRADE_1, false, RegulatorySubmissionStatus.NONE),
                ae(CtcaeGrade.GRADE_3, false, RegulatorySubmissionStatus.NONE),
                ae(CtcaeGrade.GRADE_4, false, RegulatorySubmissionStatus.NONE));
        mockTrialLookup(aes);

        var report = service.generate(trialId, from, to, tenancyId);

        assertThat(report.icsrs()).hasSize(2);
        assertThat(report.icsrs()).allMatch(
                icsr -> icsr.grade().compareTo(CtcaeGrade.GRADE_3) >= 0);
    }

    @Test
    void includesUnexpectedAeRegardlessOfGrade() {
        var aes = List.of(
                ae(CtcaeGrade.GRADE_1, true, RegulatorySubmissionStatus.NONE),
                ae(CtcaeGrade.GRADE_2, false, RegulatorySubmissionStatus.NONE));
        mockTrialLookup(aes);

        var report = service.generate(trialId, from, to, tenancyId);

        assertThat(report.icsrs()).hasSize(1);
        assertThat(report.icsrs().getFirst().unexpected()).isTrue();
    }

    @Test
    void includesAeWithRegulatorySubmission() {
        var aes = List.of(
                ae(CtcaeGrade.GRADE_1, false, RegulatorySubmissionStatus.FILED),
                ae(CtcaeGrade.GRADE_2, false, RegulatorySubmissionStatus.NONE));
        mockTrialLookup(aes);

        var report = service.generate(trialId, from, to, tenancyId);

        assertThat(report.icsrs()).hasSize(1);
        assertThat(report.icsrs().getFirst().regulatoryStatus())
                .isEqualTo(RegulatorySubmissionStatus.FILED);
    }

    @Test
    void computesPeriodSummaryByGrade() {
        var aes = List.of(
                ae(CtcaeGrade.GRADE_1, false, RegulatorySubmissionStatus.NONE),
                ae(CtcaeGrade.GRADE_3, false, RegulatorySubmissionStatus.NONE),
                ae(CtcaeGrade.GRADE_3, false, RegulatorySubmissionStatus.NONE),
                ae(CtcaeGrade.GRADE_5, true, RegulatorySubmissionStatus.FILED));
        mockTrialLookup(aes);

        var report = service.generate(trialId, from, to, tenancyId);

        assertThat(report.period().totalAdverseEvents()).isEqualTo(4);
        assertThat(report.period().byGrade().get(CtcaeGrade.GRADE_3)).isEqualTo(2);
        assertThat(report.period().byGrade().get(CtcaeGrade.GRADE_5)).isEqualTo(1);
    }

    @Test
    void filtersByPeriod() {
        var before = ae(CtcaeGrade.GRADE_4, false, RegulatorySubmissionStatus.NONE);
        before.reportedAt = Instant.parse("2025-12-01T00:00:00Z");
        var during = ae(CtcaeGrade.GRADE_4, false, RegulatorySubmissionStatus.NONE);
        during.reportedAt = Instant.parse("2026-03-15T00:00:00Z");
        mockTrialLookup(List.of(before, during));

        var report = service.generate(trialId, from, to, tenancyId);

        assertThat(report.period().totalAdverseEvents()).isEqualTo(1);
    }

    @Test
    void computesSlaCompliance() {
        var met = ae(CtcaeGrade.GRADE_3, false, RegulatorySubmissionStatus.FILED);
        var breached = ae(CtcaeGrade.GRADE_4, false, RegulatorySubmissionStatus.DEADLINE_MISSED);
        mockTrialLookup(List.of(met, breached));

        var report = service.generate(trialId, from, to, tenancyId);

        assertThat(report.slaCompliance().totalObligations()).isEqualTo(2);
        assertThat(report.slaCompliance().metWithinSla()).isEqualTo(1);
        assertThat(report.slaCompliance().breached()).isEqualTo(1);
        assertThat(report.slaCompliance().complianceRate()).isEqualTo(0.5);
    }

    @Test
    void setsMetadataCorrectly() {
        mockTrialLookup(List.of());

        var report = service.generate(trialId, from, to, tenancyId);

        assertThat(report.metadata().reportType()).isEqualTo("ind-safety");
        assertThat(report.metadata().periodStart()).isEqualTo(from);
        assertThat(report.metadata().periodEnd()).isEqualTo(to);
        assertThat(report.metadata().tenancyId()).isEqualTo(tenancyId);
    }

    private AdverseEvent ae(CtcaeGrade grade, boolean unexpected,
                            RegulatorySubmissionStatus regStatus) {
        var ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = grade;
        ae.unexpected = unexpected;
        ae.suspected = true;
        ae.regulatorySubmissionStatus = regStatus;
        ae.escalationStatus = AeEscalationStatus.NONE;
        ae.outcome = AeOutcome.ONGOING;
        ae.eventType = "headache";
        ae.reportedAt = Instant.parse("2026-03-15T10:00:00Z");
        ae.slaDeadline = ae.reportedAt.plus(grade.sla().orElse(Duration.ofDays(7)));
        return ae;
    }

    @SuppressWarnings("unchecked")
    private void mockTrialLookup(List<AdverseEvent> aes) {
        var trial = new ClinicalTrial();
        trial.id = trialId;
        trial.protocolId = "PROTO-001";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "Acme Pharma";
        trial.tenantId = tenancyId;

        var trialQuery = mock(TypedQuery.class);
        when(em.createNamedQuery("ClinicalTrial.findByIdAndTenantId", ClinicalTrial.class))
                .thenReturn(trialQuery);
        when(trialQuery.setParameter(any(String.class), any())).thenReturn(trialQuery);
        when(trialQuery.getSingleResult()).thenReturn(trial);

        var site = new TrialSite();
        site.id = UUID.randomUUID();
        site.trialId = trialId;
        site.tenantId = tenancyId;

        var siteQuery = mock(TypedQuery.class);
        when(em.createQuery(argThat((String s) -> s != null && s.contains("TrialSite")), eq(TrialSite.class)))
                .thenReturn(siteQuery);
        when(siteQuery.setParameter(any(String.class), any())).thenReturn(siteQuery);
        when(siteQuery.getResultList()).thenReturn(List.of(site));

        var enrollment = new PatientEnrollment();
        enrollment.id = UUID.randomUUID();
        enrollment.siteId = site.id;
        enrollment.tenantId = tenancyId;

        var enrollQuery = mock(TypedQuery.class);
        when(em.createQuery(argThat((String s) -> s != null && s.contains("PatientEnrollment")), eq(PatientEnrollment.class)))
                .thenReturn(enrollQuery);
        when(enrollQuery.setParameter(any(String.class), any())).thenReturn(enrollQuery);
        when(enrollQuery.getResultList()).thenReturn(List.of(enrollment));

        var aeQuery = mock(TypedQuery.class);
        when(em.createQuery(argThat((String s) -> s != null && s.contains("AdverseEvent")), eq(AdverseEvent.class)))
                .thenReturn(aeQuery);
        when(aeQuery.setParameter(any(String.class), any())).thenReturn(aeQuery);
        when(aeQuery.getResultList()).thenReturn(aes);

        when(ledgerRepo.findBySubjectId(any(), eq(tenancyId))).thenReturn(List.of());
    }
}
