package io.casehub.clinical.report;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.report.model.*;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class IndSafetyReportService {

    private final EntityManager em;
    private final LedgerEntryRepository ledgerRepo;

    @Inject
    public IndSafetyReportService(EntityManager em,
                                   LedgerEntryRepository ledgerRepo) {
        this.em = em;
        this.ledgerRepo = ledgerRepo;
    }

    public IndSafetyReport generate(UUID trialId, Instant from, Instant to,
                                     String tenancyId) {
        var trial = em.createNamedQuery("ClinicalTrial.findByIdAndTenantId",
                        ClinicalTrial.class)
                .setParameter("id", trialId)
                .setParameter("tenantId", tenancyId)
                .getSingleResult();

        var sites = em.createQuery(
                        "SELECT s FROM TrialSite s WHERE s.trialId = :trialId AND s.tenantId = :tenantId",
                        TrialSite.class)
                .setParameter("trialId", trialId)
                .setParameter("tenantId", tenancyId)
                .getResultList();

        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();
        if (siteIds.isEmpty()) {
            return emptyReport(trial, from, to, tenancyId, sites);
        }

        var enrollments = em.createQuery(
                        "SELECT e FROM PatientEnrollment e WHERE e.siteId IN :siteIds AND e.tenantId = :tenantId",
                        PatientEnrollment.class)
                .setParameter("siteIds", siteIds)
                .setParameter("tenantId", tenancyId)
                .getResultList();

        List<UUID> enrollmentIds = enrollments.stream().map(e -> e.id).toList();
        if (enrollmentIds.isEmpty()) {
            return emptyReport(trial, from, to, tenancyId, sites);
        }

        var allAes = em.createQuery(
                        "SELECT a FROM AdverseEvent a WHERE a.enrollmentId IN :enrollmentIds AND a.tenantId = :tenantId",
                        AdverseEvent.class)
                .setParameter("enrollmentIds", enrollmentIds)
                .setParameter("tenantId", tenancyId)
                .getResultList();

        var periodAes = allAes.stream()
                .filter(ae -> ae.reportedAt != null
                        && !ae.reportedAt.isBefore(from)
                        && !ae.reportedAt.isAfter(to))
                .toList();

        var metadata = new ReportMetadata("ind-safety", tenancyId,
                Instant.now(), from, to);

        var trialContext = new TrialReportContext(trial.id, trial.protocolId,
                trial.phase, trial.sponsor, sites.size(),
                enrollments.size());

        var period = buildPeriodSummary(periodAes);
        var icsrs = buildIcsrs(periodAes, tenancyId);
        var slaCompliance = buildSlaCompliance(periodAes);

        return new IndSafetyReport(metadata, trialContext, period,
                icsrs, slaCompliance);
    }

    private PeriodSummary buildPeriodSummary(List<AdverseEvent> aes) {
        Map<CtcaeGrade, Integer> byGrade = new EnumMap<>(CtcaeGrade.class);
        int susarCount = 0;
        int filed = 0;
        int breached = 0;
        int seriousUnexpected = 0;

        for (var ae : aes) {
            byGrade.merge(ae.grade, 1, Integer::sum);
            if (ae.susarOversightStatus != null
                    && ae.susarOversightStatus.name().contains("CONFIRMED")) {
                susarCount++;
            }
            if (ae.regulatorySubmissionStatus == RegulatorySubmissionStatus.FILED) {
                filed++;
            }
            if (ae.regulatorySubmissionStatus == RegulatorySubmissionStatus.DEADLINE_MISSED) {
                breached++;
            }
            if (ae.grade.compareTo(CtcaeGrade.GRADE_3) >= 0 && ae.unexpected) {
                seriousUnexpected++;
            }
        }

        return new PeriodSummary(aes.size(), byGrade, susarCount,
                filed, breached, seriousUnexpected);
    }

    private List<IndividualCaseSafetyReport> buildIcsrs(List<AdverseEvent> aes,
                                                         String tenancyId) {
        return aes.stream()
                .filter(ae -> ae.grade.compareTo(CtcaeGrade.GRADE_3) >= 0
                        || ae.unexpected
                        || ae.regulatorySubmissionStatus != RegulatorySubmissionStatus.NONE)
                .map(ae -> buildIcsr(ae, tenancyId))
                .toList();
    }

    private IndividualCaseSafetyReport buildIcsr(AdverseEvent ae, String tenancyId) {
        List<LedgerEntry> ledgerEntries = ledgerRepo.findBySubjectId(
                ae.enrollmentId, tenancyId);

        List<LedgerTraceEntry> auditTrail = ledgerEntries.stream()
                .map(e -> new LedgerTraceEntry(
                        e.getClass().getSimpleName(),
                        e.occurredAt,
                        e.actorId,
                        e.digest,
                        e.sequenceNumber))
                .toList();

        return new IndividualCaseSafetyReport(
                ae.id, ae.enrollmentId, ae.grade, ae.eventType,
                ae.reportedAt, ae.slaDeadline, ae.unexpected, ae.suspected,
                ae.outcome, ae.regulatorySubmissionStatus,
                List.of(), null, auditTrail);
    }

    private SlaComplianceSummary buildSlaCompliance(List<AdverseEvent> aes) {
        var withObligations = aes.stream()
                .filter(ae -> ae.regulatorySubmissionStatus != RegulatorySubmissionStatus.NONE)
                .toList();

        int total = withObligations.size();
        int met = (int) withObligations.stream()
                .filter(ae -> ae.regulatorySubmissionStatus == RegulatorySubmissionStatus.FILED
                        || ae.regulatorySubmissionStatus == RegulatorySubmissionStatus.PENDING)
                .count();
        int breached = (int) withObligations.stream()
                .filter(ae -> ae.regulatorySubmissionStatus == RegulatorySubmissionStatus.DEADLINE_MISSED)
                .count();

        double rate = total > 0 ? (double) met / total : 1.0;

        return new SlaComplianceSummary(total, met, breached, rate);
    }

    private IndSafetyReport emptyReport(ClinicalTrial trial, Instant from,
                                         Instant to, String tenancyId,
                                         List<TrialSite> sites) {
        return new IndSafetyReport(
                new ReportMetadata("ind-safety", tenancyId, Instant.now(), from, to),
                new TrialReportContext(trial.id, trial.protocolId, trial.phase,
                        trial.sponsor, sites.size(), 0),
                new PeriodSummary(0, Map.of(), 0, 0, 0, 0),
                List.of(),
                new SlaComplianceSummary(0, 0, 0, 1.0));
    }
}
