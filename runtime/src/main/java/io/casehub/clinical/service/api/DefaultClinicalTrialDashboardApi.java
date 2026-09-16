package io.casehub.clinical.service.api;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.api.ClinicalCapabilities;
import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.ClinicalTrustDimensions;
import io.casehub.clinical.api.model.AePrecedentResponse;
import io.casehub.clinical.api.model.AePrecedentSearchResponse;
import io.casehub.clinical.api.model.DeviationPrecedentResponse;
import io.casehub.clinical.api.model.DeviationPrecedentSearchResponse;
import io.casehub.clinical.api.model.PlanStepResponse;
import io.casehub.clinical.api.spi.ClinicalTrialDashboardApi;
import io.casehub.clinical.api.view.AeTrajectoryMatchView;
import io.casehub.clinical.api.view.AeTrajectoryView;
import io.casehub.clinical.api.view.AgentTrustView;
import io.casehub.clinical.api.view.DashboardAdverseEventView;
import io.casehub.clinical.api.view.DashboardDeviationView;
import io.casehub.clinical.api.view.DashboardGradeChangeView;
import io.casehub.clinical.api.view.DashboardPatientView;
import io.casehub.clinical.api.view.DashboardSiteView;
import io.casehub.clinical.api.view.DimensionTrendView;
import io.casehub.clinical.api.view.EnrollmentObservationView;
import io.casehub.clinical.api.view.GovernanceContextView;
import io.casehub.clinical.api.view.LedgerEntryView;
import io.casehub.clinical.api.view.SiteEnrollmentTrajectoryView;
import io.casehub.clinical.api.view.TrajectoryMatchView;
import io.casehub.clinical.api.view.TrajectoryObservationView;
import io.casehub.clinical.api.view.TrajectoryTrendSummaryView;
import io.casehub.clinical.api.view.TrialSummaryView;
import io.casehub.clinical.cbr.AeCbrFeatureBuilder;
import io.casehub.clinical.cbr.ClinicalCbrDomains;
import io.casehub.clinical.cbr.ClinicalCbrSchemaInitializer;
import io.casehub.clinical.cbr.ClinicalCbrService;
import io.casehub.clinical.cbr.ClinicalScopeResolver;
import io.casehub.clinical.cbr.AeCbrContext;
import io.casehub.clinical.cbr.AeTrajectoryBuilder;
import io.casehub.clinical.cbr.SiteEnrollmentTrajectoryBuilder;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.AeGradeChange;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.IrbApproval;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TenantEntityLookup;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.routing.ClinicalTrustRoutingPolicyProvider;
import io.casehub.clinical.service.CommitmentLifecycleService;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.ledger.repository.CaseLedgerEntryRepository;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.neocortex.memory.cbr.CbrFilter;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.cbr.TrendAnalyzer;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
@Transactional
public class DefaultClinicalTrialDashboardApi implements ClinicalTrialDashboardApi {

    private static final List<String[]> CAPABILITY_DIMENSIONS = List.of(
            new String[]{ClinicalCapabilities.ELIGIBILITY_SCREENING, ClinicalTrustDimensions.ELIGIBILITY_PRECISION},
            new String[]{ClinicalCapabilities.SAFETY_MONITORING, ClinicalTrustDimensions.SAFETY_ACCURACY},
            new String[]{ClinicalCapabilities.PROTOCOL_REVIEW, ClinicalTrustDimensions.PROTOCOL_ADHERENCE},
            new String[]{ClinicalCapabilities.IRB_CONSULTATION, ClinicalTrustDimensions.PROTOCOL_ADHERENCE},
            new String[]{ClinicalCapabilities.PI_AUTHORISATION, ClinicalTrustDimensions.PROTOCOL_ADHERENCE},
            new String[]{ClinicalCapabilities.DATA_SAFETY_MONITORING, ClinicalTrustDimensions.SAFETY_ACCURACY},
            new String[]{ClinicalCapabilities.REGULATORY_SUBMISSION, ClinicalTrustDimensions.SAFETY_ACCURACY},
            new String[]{ClinicalCapabilities.TRIAL_SUPERVISOR, ClinicalTrustDimensions.PROTOCOL_ADHERENCE}
    );

    @Inject EntityManager em;
    @Inject CurrentPrincipal principal;
    @Inject ActorTrustScoreRepository trustScoreRepository;
    @Inject ClinicalTrustRoutingPolicyProvider trustRoutingPolicyProvider;
    @Inject CaseLedgerEntryRepository caseLedgerEntryRepository;
    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject ClinicalCbrService cbrService;
    @Inject ClinicalScopeResolver scopeResolver;
    @Inject AeTrajectoryBuilder aeTrajectoryBuilder;
    @Inject SiteEnrollmentTrajectoryBuilder siteEnrollmentTrajectoryBuilder;
    @Inject CommitmentLifecycleService commitmentLifecycleService;

    @Override
    public TrialSummaryView summary(UUID trialId, String tenancyId) {
        ClinicalTrial trial = TenantEntityLookup.findByIdForTenant(em, ClinicalTrial.class, trialId, principal);
        if (trial == null) throw new NotFoundException("Trial not found: " + trialId);

        List<TrialSite> sites = em.createQuery("SELECT s FROM TrialSite s WHERE s.trialId = :trialId AND s.tenantId = :tenantId", TrialSite.class).setParameter("trialId", trialId).setParameter("tenantId", tenancyId).getResultList();
        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();

        long enrolled = siteIds.isEmpty() ? 0 :
                em.createQuery("SELECT COUNT(e) FROM PatientEnrollment e WHERE e.siteId IN :siteIds AND e.tenantId = :tenantId", Long.class).setParameter("siteIds", siteIds).setParameter("tenantId", tenancyId).getSingleResult();

        long aeCount = 0;
        if (!siteIds.isEmpty()) {
            List<PatientEnrollment> enrollments = em.createQuery("SELECT e FROM PatientEnrollment e WHERE e.siteId IN :siteIds AND e.tenantId = :tenantId", PatientEnrollment.class)
                    .setParameter("siteIds", siteIds).setParameter("tenantId", tenancyId).getResultList();
            List<UUID> enrollmentIds = enrollments.stream().map(e -> e.id).toList();
            if (!enrollmentIds.isEmpty()) {
                aeCount = em.createQuery("SELECT COUNT(a) FROM AdverseEvent a WHERE a.enrollmentId IN :enrollmentIds AND a.tenantId = :tenantId", Long.class).setParameter("enrollmentIds", enrollmentIds).setParameter("tenantId", tenancyId).getSingleResult();
            }
        }

        long devCount = siteIds.isEmpty() ? 0 :
                em.createQuery("SELECT COUNT(d) FROM ProtocolDeviation d WHERE d.siteId IN :siteIds AND d.tenantId = :tenantId", Long.class).setParameter("siteIds", siteIds).setParameter("tenantId", tenancyId).getSingleResult();

        return new TrialSummaryView(trial.protocolId, trial.phase.name(), trial.sponsor,
                trial.targetEnrollment, enrolled, aeCount, devCount);
    }

    @Override
    public List<DashboardPatientView> patients(UUID trialId, String tenancyId) {
        ClinicalTrial trial = TenantEntityLookup.findByIdForTenant(em, ClinicalTrial.class, trialId, principal);
        if (trial == null) throw new NotFoundException("Trial not found: " + trialId);

        List<TrialSite> sites = em.createQuery("SELECT s FROM TrialSite s WHERE s.trialId = :trialId AND s.tenantId = :tenantId", TrialSite.class).setParameter("trialId", trialId).setParameter("tenantId", tenancyId).getResultList();
        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();
        if (siteIds.isEmpty()) return List.of();

        List<PatientEnrollment> enrollments = em.createQuery("SELECT e FROM PatientEnrollment e WHERE e.siteId IN :siteIds AND e.tenantId = :tenantId", PatientEnrollment.class)
                .setParameter("siteIds", siteIds).setParameter("tenantId", tenancyId).getResultList();

        return enrollments.stream().map(e -> new DashboardPatientView(
                e.id, e.siteId, e.patientId,
                e.enrollmentStatus != null ? e.enrollmentStatus.name() : null,
                e.screeningResult != null ? e.screeningResult.name() : null,
                e.consentStatus != null ? e.consentStatus.name() : null
        )).toList();
    }

    @Override
    public List<DashboardAdverseEventView> adverseEvents(UUID trialId, String tenancyId) {
        ClinicalTrial trial = TenantEntityLookup.findByIdForTenant(em, ClinicalTrial.class, trialId, principal);
        if (trial == null) throw new NotFoundException("Trial not found: " + trialId);

        List<TrialSite> sites = em.createQuery("SELECT s FROM TrialSite s WHERE s.trialId = :trialId AND s.tenantId = :tenantId", TrialSite.class).setParameter("trialId", trialId).setParameter("tenantId", tenancyId).getResultList();
        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();
        if (siteIds.isEmpty()) return List.of();

        List<PatientEnrollment> enrollments = em.createQuery("SELECT e FROM PatientEnrollment e WHERE e.siteId IN :siteIds AND e.tenantId = :tenantId", PatientEnrollment.class)
                .setParameter("siteIds", siteIds).setParameter("tenantId", tenancyId).getResultList();
        if (enrollments.isEmpty()) return List.of();

        List<UUID> enrollmentIds = enrollments.stream().map(e -> e.id).toList();
        Map<UUID, UUID> enrollmentToSite = enrollments.stream()
                .collect(Collectors.toMap(e -> e.id, e -> e.siteId));
        Map<UUID, String> siteIdToName = sites.stream()
                .collect(Collectors.toMap(s -> s.id, s -> s.investigatorId));
        Map<UUID, String> enrollmentToPatientId = enrollments.stream()
                .collect(Collectors.toMap(e -> e.id, e -> e.patientId));

        List<AdverseEvent> aes = em.createQuery("SELECT a FROM AdverseEvent a WHERE a.enrollmentId IN :enrollmentIds AND a.tenantId = :tenantId", AdverseEvent.class)
                .setParameter("enrollmentIds", enrollmentIds).setParameter("tenantId", tenancyId).getResultList();

        Instant now = Instant.now();
        return aes.stream().map(ae -> {
            String slaRemaining = null;
            Double slaHours = null;
            if (ae.slaDeadline != null) {
                Duration remaining = Duration.between(now, ae.slaDeadline);
                slaHours = remaining.toMinutes() / 60.0;
                slaRemaining = remaining.isNegative()
                        ? "OVERDUE by " + formatDuration(remaining.abs())
                        : formatDuration(remaining) + " remaining";
            }
            var gradeHistory = em.createNamedQuery("AeGradeChange.findByAdverseEventId", AeGradeChange.class).setParameter("aeId", ae.id).getResultList().stream()
                    .map(gc -> new DashboardGradeChangeView(
                            gc.previousGrade != null ? gc.previousGrade.name() : null,
                            gc.newGrade.name(), gc.changedAt, gc.changedBy))
                    .toList();
            return new DashboardAdverseEventView(
                    ae.id, ae.enrollmentId, enrollmentToSite.get(ae.enrollmentId),
                    siteIdToName.get(enrollmentToSite.get(ae.enrollmentId)),
                    enrollmentToPatientId.get(ae.enrollmentId),
                    ae.grade != null ? ae.grade.name() : null, ae.eventType,
                    ae.reportedAt, ae.slaDeadline,
                    ae.escalationStatus != null ? ae.escalationStatus.name() : null,
                    ae.regulatorySubmissionStatus != null ? ae.regulatorySubmissionStatus.name() : null,
                    slaRemaining, slaHours, gradeHistory);
        }).toList();
    }

    @Override
    public List<DashboardDeviationView> deviations(UUID trialId, String tenancyId) {
        ClinicalTrial trial = TenantEntityLookup.findByIdForTenant(em, ClinicalTrial.class, trialId, principal);
        if (trial == null) throw new NotFoundException("Trial not found: " + trialId);

        List<TrialSite> sites = em.createQuery("SELECT s FROM TrialSite s WHERE s.trialId = :trialId AND s.tenantId = :tenantId", TrialSite.class).setParameter("trialId", trialId).setParameter("tenantId", tenancyId).getResultList();
        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();
        if (siteIds.isEmpty()) return List.of();

        Map<UUID, String> siteIdToName = sites.stream()
                .collect(Collectors.toMap(s -> s.id, s -> s.investigatorId));

        List<ProtocolDeviation> devs = em.createQuery("SELECT d FROM ProtocolDeviation d WHERE d.siteId IN :siteIds AND d.tenantId = :tenantId", ProtocolDeviation.class)
                .setParameter("siteIds", siteIds).setParameter("tenantId", tenancyId).getResultList();

        List<UUID> devIds = devs.stream().map(d -> d.id).toList();
        Map<UUID, String> irbDecisionByDeviation = devIds.isEmpty() ? Map.of() :
                em.createQuery("SELECT i FROM IrbApproval i WHERE i.deviationId IN :devIds AND i.tenantId = :tenantId", IrbApproval.class)
                        .setParameter("devIds", devIds).setParameter("tenantId", tenancyId).getResultStream()
                        .collect(Collectors.toMap(irb -> irb.deviationId, irb -> irb.decision.name(), (a, b) -> a));

        return devs.stream().map(d -> new DashboardDeviationView(
                d.id, d.siteId, siteIdToName.get(d.siteId), d.deviationType,
                d.severity != null ? d.severity.name() : null,
                d.piApprovalStatus != null ? d.piApprovalStatus.name() : null,
                d.commandedAt, irbDecisionByDeviation.get(d.id)
        )).toList();
    }

    @Override
    public List<AgentTrustView> agents(UUID trialId, String tenancyId) {
        ClinicalTrial trial = TenantEntityLookup.findByIdForTenant(em, ClinicalTrial.class, trialId, principal);
        if (trial == null) throw new NotFoundException("Trial not found: " + trialId);

        String distinctDimensions = CAPABILITY_DIMENSIONS.stream()
                .map(pair -> pair[1]).distinct().sorted()
                .collect(Collectors.joining(", "));

        return CAPABILITY_DIMENSIONS.stream().map(cd -> {
            String capability = cd[0];
            String dimension = cd[1];
            TrustRoutingPolicy policy = trustRoutingPolicyProvider.forCapability(capability);

            List<ActorTrustScore> scores = trustScoreRepository.findAll().stream()
                    .filter(s -> capability.equals(s.capabilityKey)).toList();

            if (scores.isEmpty()) {
                return new AgentTrustView(capability, dimension, null,
                        policy.threshold(), "bootstrap", 0, 0, 0, null, distinctDimensions);
            }

            double avgScore = scores.stream().mapToDouble(s -> s.trustScore).average().orElse(0.0);
            int totalDecisions = scores.stream().mapToInt(s -> s.decisionCount).sum();
            int totalPositive = scores.stream().mapToInt(s -> s.attestationPositive).sum();
            int totalNegative = scores.stream().mapToInt(s -> s.attestationNegative).sum();
            String maturity = totalDecisions < 10 ? "bootstrap" : totalDecisions < 50 ? "emerging" : "established";
            int totalAttestations = totalPositive + totalNegative;
            Double endorsementRatio = totalAttestations == 0 ? null : (double) totalPositive / totalAttestations;

            return new AgentTrustView(capability, dimension, avgScore,
                    policy.threshold(), maturity, totalDecisions, totalPositive, totalNegative,
                    endorsementRatio, distinctDimensions);
        }).toList();
    }

    @Override
    public GovernanceContextView governance(UUID trialId, UUID aeId, String tenancyId) {
        ClinicalTrial trial = TenantEntityLookup.findByIdForTenant(em, ClinicalTrial.class, trialId, principal);
        if (trial == null) throw new NotFoundException("Trial not found: " + trialId);

        AdverseEvent ae = TenantEntityLookup.findByIdForTenant(em, AdverseEvent.class, aeId, principal);
        if (ae == null) throw new NotFoundException("Adverse event not found: " + aeId);

        PatientEnrollment enrollment = TenantEntityLookup.findByIdForTenant(em, PatientEnrollment.class, ae.enrollmentId, principal);
        if (enrollment == null) throw new NotFoundException("Enrollment not found");
        TrialSite site = TenantEntityLookup.findByIdForTenant(em, TrialSite.class, enrollment.siteId, principal);
        if (site == null || !site.trialId.equals(trialId)) throw new NotFoundException("Site not found or mismatch");

        String workerId = null;
        String capabilityTag = null;
        Double trustScoreAtRouting = null;
        Double thresholdApplied = null;
        Double currentTrustScore = null;
        String gateStatus = ae.susarOversightStatus.name();

        if (ae.susarOversightCaseId != null) {
            List<WorkerDecisionEntry> decisions =
                    caseLedgerEntryRepository.findWorkerDecisionsByCaseId(ae.susarOversightCaseId);
            Optional<WorkerDecisionEntry> safetyDecision = decisions.stream()
                    .filter(e -> ClinicalCapabilities.SAFETY_MONITORING.equals(e.capabilityTag))
                    .findFirst();

            if (safetyDecision.isPresent()) {
                WorkerDecisionEntry entry = safetyDecision.get();
                workerId = entry.workerId;
                capabilityTag = entry.capabilityTag;
                trustScoreAtRouting = entry.trustScoreAtRouting;
                thresholdApplied = entry.thresholdApplied;

                if (workerId != null) {
                    currentTrustScore = trustScoreRepository
                            .findCapabilityScore(workerId, ClinicalCapabilities.SAFETY_MONITORING)
                            .map(s -> s.trustScore).orElse(null);
                }
            }
        }

        return new GovernanceContextView(
                ae.grade != null ? ae.grade.name() : null,
                ae.unexpected, ae.suspected, ae.susarOversightStatus.name(),
                workerId, capabilityTag, trustScoreAtRouting, thresholdApplied,
                currentTrustScore, gateStatus);
    }

    @Override
    public List<LedgerEntryView> ledgerEntries(UUID trialId, String typeFilter, String tenancyId) {
        ClinicalTrial trial = TenantEntityLookup.findByIdForTenant(em, ClinicalTrial.class, trialId, principal);
        if (trial == null) throw new NotFoundException("Trial not found: " + trialId);

        List<TrialSite> sites = em.createQuery("SELECT s FROM TrialSite s WHERE s.trialId = :trialId AND s.tenantId = :tenantId", TrialSite.class).setParameter("trialId", trialId).setParameter("tenantId", tenancyId).getResultList();
        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();
        if (siteIds.isEmpty()) return List.of();

        List<PatientEnrollment> enrollments = em.createQuery("SELECT e FROM PatientEnrollment e WHERE e.siteId IN :siteIds AND e.tenantId = :tenantId", PatientEnrollment.class)
                .setParameter("siteIds", siteIds).setParameter("tenantId", tenancyId).getResultList();
        List<UUID> enrollmentIds = enrollments.stream().map(e -> e.id).toList();

        List<ProtocolDeviation> deviations = em.createQuery("SELECT d FROM ProtocolDeviation d WHERE d.siteId IN :siteIds AND d.tenantId = :tenantId", ProtocolDeviation.class)
                .setParameter("siteIds", siteIds).setParameter("tenantId", tenancyId).getResultList();
        List<UUID> deviationIds = deviations.stream().map(d -> d.id).toList();

        List<UUID> aeIds = List.of();
        if (!enrollmentIds.isEmpty()) {
            List<AdverseEvent> aes = em.createQuery("SELECT a FROM AdverseEvent a WHERE a.enrollmentId IN :enrollmentIds AND a.tenantId = :tenantId", AdverseEvent.class)
                    .setParameter("enrollmentIds", enrollmentIds).setParameter("tenantId", tenancyId).getResultList();
            aeIds = aes.stream().map(ae -> ae.id).toList();
        }

        List<UUID> allSubjectIds = Stream.of(enrollmentIds.stream(), deviationIds.stream(), aeIds.stream())
                .flatMap(s -> s).toList();
        if (allSubjectIds.isEmpty()) return List.of();

        return allSubjectIds.stream()
                .flatMap(subjectId -> ledgerEntryRepository.findBySubjectId(subjectId, "default").stream())
                .filter(entry -> typeFilter == null || typeFilter.isEmpty()
                        || entry.entryType.name().equalsIgnoreCase(typeFilter))
                .sorted(Comparator.comparing(entry -> entry.occurredAt != null ? entry.occurredAt : Instant.EPOCH))
                .map(entry -> new LedgerEntryView(
                        entry.id, entry.subjectId, entry.sequenceNumber,
                        entry.entryType != null ? entry.entryType.name() : null,
                        entry.actorId, entry.actorRole, entry.occurredAt,
                        entry.digest, buildLedgerSummary(entry)))
                .toList();
    }

    @Override
    public List<DashboardSiteView> sites(UUID trialId, String tenancyId) {
        ClinicalTrial trial = TenantEntityLookup.findByIdForTenant(em, ClinicalTrial.class, trialId, principal);
        if (trial == null) throw new NotFoundException("Trial not found: " + trialId);

        List<TrialSite> sites = em.createQuery("SELECT s FROM TrialSite s WHERE s.trialId = :trialId AND s.tenantId = :tenantId", TrialSite.class).setParameter("trialId", trialId).setParameter("tenantId", tenancyId).getResultList();
        if (sites.isEmpty()) return List.of();

        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();

        List<PatientEnrollment> enrollments = em.createQuery("SELECT e FROM PatientEnrollment e WHERE e.siteId IN :siteIds AND e.tenantId = :tenantId", PatientEnrollment.class)
                .setParameter("siteIds", siteIds).setParameter("tenantId", tenancyId).getResultList();
        Map<UUID, Long> enrolledBySite = enrollments.stream()
                .collect(Collectors.groupingBy(e -> e.siteId, Collectors.counting()));

        Map<UUID, Long> aeBySite = new HashMap<>();
        if (!enrollments.isEmpty()) {
            List<UUID> enrollmentIds = enrollments.stream().map(e -> e.id).toList();
            Map<UUID, UUID> enrollmentToSite = enrollments.stream()
                    .collect(Collectors.toMap(e -> e.id, e -> e.siteId));
            List<AdverseEvent> aes = em.createQuery("SELECT a FROM AdverseEvent a WHERE a.enrollmentId IN :enrollmentIds AND a.tenantId = :tenantId", AdverseEvent.class)
                    .setParameter("enrollmentIds", enrollmentIds).setParameter("tenantId", tenancyId).getResultList();
            aeBySite = aes.stream()
                    .collect(Collectors.groupingBy(ae -> enrollmentToSite.get(ae.enrollmentId), Collectors.counting()));
        }

        Map<UUID, Long> devBySite = em.createQuery("SELECT d FROM ProtocolDeviation d WHERE d.siteId IN :siteIds AND d.tenantId = :tenantId", ProtocolDeviation.class)
                .setParameter("siteIds", siteIds).setParameter("tenantId", tenancyId)
                .getResultList().stream()
                .collect(Collectors.groupingBy(d -> d.siteId, Collectors.counting()));

        Map<UUID, Long> finalAeBySite = aeBySite;
        return sites.stream().map(s -> new DashboardSiteView(
                s.id, s.investigatorId, s.investigatorId,
                s.status != null ? s.status.name() : null,
                enrolledBySite.getOrDefault(s.id, 0L),
                finalAeBySite.getOrDefault(s.id, 0L),
                devBySite.getOrDefault(s.id, 0L),
                s.targetEnrollment
        )).toList();
    }

    @Override
    public AePrecedentSearchResponse aePrecedents(UUID trialId, UUID aeId, String tenancyId, String actorId) {
        AdverseEvent ae = TenantEntityLookup.findByIdForTenant(em, AdverseEvent.class, aeId, principal);
        if (ae == null) throw new NotFoundException("Adverse event not found: " + aeId);

        PatientEnrollment enrollment = ae.enrollmentId != null
                ? TenantEntityLookup.findByIdForTenant(em, PatientEnrollment.class, ae.enrollmentId, principal) : null;
        TrialSite site = enrollment != null && enrollment.siteId != null
                ? TenantEntityLookup.findByIdForTenant(em, TrialSite.class, enrollment.siteId, principal) : null;
        ClinicalTrial trial = site != null && site.trialId != null
                ? TenantEntityLookup.findByIdForTenant(em, ClinicalTrial.class, site.trialId, principal) : null;

        long priorAeCount = ae.enrollmentId != null
                ? em.createQuery("SELECT COUNT(a) FROM AdverseEvent a WHERE a.enrollmentId = :enrollmentId AND a.id != :excludeId", Long.class).setParameter("enrollmentId", ae.enrollmentId).setParameter("excludeId", aeId).getSingleResult() : 0;
        long siteEnrollmentCount = site != null ? em.createQuery("SELECT COUNT(e) FROM PatientEnrollment e WHERE e.siteId = :siteId", Long.class).setParameter("siteId", site.id).getSingleResult() : 0;
        int siteTargetEnrollment = site != null ? site.targetEnrollment : 0;

        var ctx = new AeCbrContext(ae, enrollment, trial, null, false,
                priorAeCount, siteEnrollmentCount, siteTargetEnrollment, 0.5);
        Map<String, Object> features = AeCbrFeatureBuilder.buildQueryFeatures(ctx);

        Path queryScope = scopeResolver.forAdverseEvent(ae).orElse(Path.root());
        CbrQuery query = CbrQuery.of(tenancyId, ClinicalCbrDomains.AE,
                        queryScope, "clinical-ae", FeatureValue.toFeatureMap(features), 10)
                .withMinSimilarity(0.3).withVectorWeight(0.0)
                .withWeight("grade", 3.0).withWeight("eventType", 2.5)
                .withWeight("treatmentArm", 1.5).withWeight("unexpected", 1.5)
                .withWeight("priorAeCount", 1.0).withWeight("trialPhase", 1.0)
                .withWeight("suspected", 1.0)
                .withWeight("safetyReviewOutcome", 0.0)
                .withWeight("dsmbEscalated", 0.0).withWeight("indReportFiled", 0.0)
                .withWeight("susarOversight", 0.0);

        var result = cbrService.retrieveWithAudit(query, FeatureVectorCbrCase.class, aeId, actorId);
        List<AePrecedentResponse> precedents = result.cases().stream().map(this::mapToAeResponse).toList();
        return new AePrecedentSearchResponse(result.traceId(), result.explanation(), precedents);
    }

    @Override
    public DeviationPrecedentSearchResponse deviationPrecedents(UUID trialId, UUID devId, String tenancyId, String actorId) {
        ProtocolDeviation deviation = TenantEntityLookup.findByIdForTenant(em, ProtocolDeviation.class, devId, principal);
        if (deviation == null) throw new NotFoundException("Deviation not found: " + devId);

        Map<String, Object> features = Map.of(
                "deviationType", deviation.deviationType != null ? deviation.deviationType : "UNKNOWN",
                "severity", deviation.severity != null ? deviation.severity.name() : "UNKNOWN",
                "escalationRequirement", deviation.escalationRequirement != null
                        ? deviation.escalationRequirement.name() : "UNKNOWN");

        Path devQueryScope = scopeResolver.forDeviation(deviation).orElse(Path.root());
        CbrQuery query = CbrQuery.of(tenancyId, ClinicalCbrDomains.DEVIATION,
                        devQueryScope, "clinical-deviation", FeatureValue.toFeatureMap(features), 10)
                .withMinSimilarity(0.3).withVectorWeight(0.0)
                .withWeight("piDecision", 0.0).withWeight("irbDecision", 0.0);

        var result = cbrService.retrieveWithAudit(query, FeatureVectorCbrCase.class, devId, actorId);
        List<DeviationPrecedentResponse> precedents = result.cases().stream()
                .map(this::mapToDeviationResponse).toList();
        return new DeviationPrecedentSearchResponse(result.traceId(), result.explanation(), precedents);
    }

    @Override
    public Object getCommitmentLifecycle(UUID trialId, UUID devId, String tenancyId) {
        ProtocolDeviation deviation = TenantEntityLookup.findByIdForTenant(em, ProtocolDeviation.class, devId, principal);
        if (deviation == null) throw new NotFoundException("Deviation not found: " + devId);

        TrialSite site = TenantEntityLookup.findByIdForTenant(em, TrialSite.class, deviation.siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            throw new NotFoundException("Site not found or trial mismatch");

        return commitmentLifecycleService.buildResponse(deviation, principal)
                .orElseThrow(() -> new NotFoundException("No commitment lifecycle found"));
    }

    @Override
    public AeTrajectoryView aeTrajectory(UUID trialId, UUID aeId, String tenancyId) {
        AdverseEvent ae = TenantEntityLookup.findByIdForTenant(em, AdverseEvent.class, aeId, principal);
        if (ae == null) throw new NotFoundException("Adverse event not found: " + aeId);

        var observations = aeTrajectoryBuilder.buildPartialTrajectory(ae, tenancyId);
        var obsViews = observations.stream().map(this::toTrajectoryObs).toList();
        TrajectoryTrendSummaryView trends = buildAeTrends(observations);
        return new AeTrajectoryView(aeId, obsViews, trends);
    }

    @Override
    public AeTrajectoryMatchView aeTrajectoryMatches(UUID trialId, UUID aeId,
                                                      int limit, double minScore, String tenancyId) {
        AdverseEvent ae = TenantEntityLookup.findByIdForTenant(em, AdverseEvent.class, aeId, principal);
        if (ae == null) throw new NotFoundException("Adverse event not found: " + aeId);

        var trajectory = aeTrajectoryBuilder.buildPartialTrajectory(ae, tenancyId);
        Map<String, FeatureValue> features = new LinkedHashMap<>();
        features.put("grade", FeatureValue.number(ae.grade != null ? ae.grade.ordinal() + 1 : 0));
        features.put("trialPhase", FeatureValue.string("UNKNOWN"));
        features.put("unexpected", FeatureValue.string(String.valueOf(ae.unexpected)));
        features.put("suspected", FeatureValue.string(String.valueOf(ae.suspected)));
        features.put("aeTrajectory", FeatureValue.structList(trajectory));

        Path trajQueryScope = scopeResolver.forAdverseEvent(ae).orElse(Path.root());
        CbrQuery query = CbrQuery.of(tenancyId, ClinicalCbrDomains.AE_TRAJECTORY,
                        trajQueryScope, "clinical-ae-trajectory", features, limit)
                .withMinSimilarity(minScore)
                .withFilter("eventType", CbrFilter.contains(ae.eventType != null ? ae.eventType : "UNKNOWN"));

        var result = cbrService.retrieveWithAudit(query, FeatureVectorCbrCase.class,
                ae.enrollmentId, ClinicalActors.CLINICAL_SERVICE);

        var matches = result.cases().stream().map(sc -> {
            FeatureValue trajVal = sc.cbrCase().features().get("aeTrajectory");
            List<TrajectoryObservationView> matchTraj = List.of();
            if (trajVal instanceof FeatureValue.StructListVal sl) {
                matchTraj = sl.items().stream().map(this::toTrajectoryObs).toList();
            }
            return new TrajectoryMatchView(sc.caseId(), sc.score(), sc.cbrCase().outcome(), matchTraj);
        }).toList();

        return new AeTrajectoryMatchView(matches, result.traceId(), result.explanation());
    }

    @Override
    public SiteEnrollmentTrajectoryView siteEnrollmentTrajectory(UUID trialId, UUID siteId, String tenancyId) {
        TrialSite site = TenantEntityLookup.findByIdForTenant(em, TrialSite.class, siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            throw new NotFoundException("Site not found or trial mismatch");

        Instant earliest = em.createQuery("SELECT e FROM PatientEnrollment e WHERE e.siteId = :siteId AND e.tenantId = :tenantId AND e.enrolledAt IS NOT NULL ORDER BY e.enrolledAt ASC", PatientEnrollment.class)
                .setParameter("siteId", siteId).setParameter("tenantId", tenancyId)
                .getResultStream().findFirst().map(e -> e.enrolledAt).orElse(null);

        if (earliest == null) {
            return new SiteEnrollmentTrajectoryView(List.of(), new TrajectoryTrendSummaryView(Map.of()));
        }

        var trajectory = siteEnrollmentTrajectoryBuilder.buildTrajectory(siteId, trialId, earliest, tenancyId);
        var obsViews = trajectory.stream().map(this::toEnrollmentObs).toList();
        TrajectoryTrendSummaryView trends = buildEnrollmentTrends(trajectory);
        return new SiteEnrollmentTrajectoryView(obsViews, trends);
    }

    // --- Private helpers ---

    private static String buildLedgerSummary(LedgerEntry entry) {
        if (entry.entryType == null) return null;
        String actor = entry.actorId != null ? entry.actorId : "system";
        return entry.entryType.name() + " by " + actor
                + (entry.actorRole != null ? " (" + entry.actorRole + ")" : "");
    }

    private static String formatDuration(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        if (hours > 24) return (hours / 24) + "d " + (hours % 24) + "h";
        return hours + "h " + minutes + "m";
    }

    private TrajectoryObservationView toTrajectoryObs(Map<String, FeatureValue> obs) {
        return new TrajectoryObservationView(
                (long) ((FeatureValue.NumberVal) obs.get("ts")).value(),
                (int) ((FeatureValue.NumberVal) obs.get("escalation")).value(),
                (int) ((FeatureValue.NumberVal) obs.get("susar")).value(),
                (int) ((FeatureValue.NumberVal) obs.get("regulatory")).value());
    }

    private EnrollmentObservationView toEnrollmentObs(Map<String, FeatureValue> obs) {
        return new EnrollmentObservationView(
                (int) ((FeatureValue.NumberVal) obs.get("ts")).value(),
                (int) ((FeatureValue.NumberVal) obs.get("periodCount")).value(),
                (int) ((FeatureValue.NumberVal) obs.get("cumulativeCount")).value());
    }

    private TrajectoryTrendSummaryView buildAeTrends(List<Map<String, FeatureValue>> observations) {
        if (observations.size() < 2) return new TrajectoryTrendSummaryView(Map.of());
        var schema = ClinicalCbrSchemaInitializer.aeTrajectorySchema();
        var tsField = schema.fields().stream()
                .filter(f -> f instanceof FeatureField.TimeSeries)
                .map(f -> (FeatureField.TimeSeries) f)
                .findFirst().orElse(null);
        if (tsField == null) return new TrajectoryTrendSummaryView(Map.of());

        var profile = TrendAnalyzer.analyze(observations, tsField);
        Map<String, DimensionTrendView> dims = new LinkedHashMap<>();
        for (String dim : List.of("escalation", "susar", "regulatory")) {
            dims.put(dim, new DimensionTrendView(
                    profile.metrics().getOrDefault("aeTrajectory." + dim + ".slope", 0.0),
                    profile.metrics().getOrDefault("aeTrajectory." + dim + ".acceleration", 0.0),
                    profile.metrics().getOrDefault("aeTrajectory." + dim + ".changePoints", 0.0).intValue()));
        }
        return new TrajectoryTrendSummaryView(dims);
    }

    private TrajectoryTrendSummaryView buildEnrollmentTrends(List<Map<String, FeatureValue>> observations) {
        if (observations.size() < 2) return new TrajectoryTrendSummaryView(Map.of());
        var schema = ClinicalCbrSchemaInitializer.siteEnrollmentSchema();
        var tsField = schema.fields().stream()
                .filter(f -> f instanceof FeatureField.TimeSeries)
                .map(f -> (FeatureField.TimeSeries) f)
                .findFirst().orElse(null);
        if (tsField == null) return new TrajectoryTrendSummaryView(Map.of());

        var profile = TrendAnalyzer.analyze(observations, tsField);
        Map<String, DimensionTrendView> dims = new LinkedHashMap<>();
        for (String dim : List.of("periodCount", "cumulativeCount")) {
            dims.put(dim, new DimensionTrendView(
                    profile.metrics().getOrDefault("enrollmentRate." + dim + ".slope", 0.0),
                    profile.metrics().getOrDefault("enrollmentRate." + dim + ".acceleration", 0.0),
                    profile.metrics().getOrDefault("enrollmentRate." + dim + ".changePoints", 0.0).intValue()));
        }
        return new TrajectoryTrendSummaryView(dims);
    }

    private static String extractFirst(Object value, String fallback) {
        if (value instanceof List<?> list && !list.isEmpty()) return String.valueOf(list.getFirst());
        return value != null ? String.valueOf(value) : fallback;
    }

    private AePrecedentResponse mapToAeResponse(ScoredCbrCase<FeatureVectorCbrCase> scored) {
        FeatureVectorCbrCase c = scored.cbrCase();
        Map<String, Object> features = FeatureValue.toRawMap(c.features());

        Object gradeObj = features.get("grade");
        int gradeInt = gradeObj instanceof Number ? ((Number) gradeObj).intValue() : 0;
        String gradeStr = gradeInt > 0 && gradeInt <= 5 ? "GRADE_" + gradeInt : "UNKNOWN";

        return new AePrecedentResponse(
                scored.score(), gradeStr,
                extractFirst(features.get("eventType"), "UNKNOWN"),
                String.valueOf(features.getOrDefault("trialPhase", "UNKNOWN")),
                "true".equals(String.valueOf(features.get("unexpected"))),
                "true".equals(String.valueOf(features.get("suspected"))),
                String.valueOf(features.getOrDefault("treatmentArm", "UNASSIGNED")),
                String.valueOf(features.getOrDefault("priorAeCount", "NONE")),
                String.valueOf(features.getOrDefault("safetyReviewOutcome", "UNKNOWN")),
                "true".equals(String.valueOf(features.get("dsmbEscalated"))),
                "true".equals(String.valueOf(features.get("indReportFiled"))),
                "true".equals(String.valueOf(features.get("susarOversight"))),
                List.of(), c.problem(), c.outcome());
    }

    private DeviationPrecedentResponse mapToDeviationResponse(ScoredCbrCase<FeatureVectorCbrCase> scored) {
        FeatureVectorCbrCase c = scored.cbrCase();
        Map<String, Object> features = FeatureValue.toRawMap(c.features());

        return new DeviationPrecedentResponse(
                scored.score(),
                String.valueOf(features.getOrDefault("deviationType", "UNKNOWN")),
                String.valueOf(features.getOrDefault("severity", "UNKNOWN")),
                String.valueOf(features.getOrDefault("escalationRequirement", "UNKNOWN")),
                String.valueOf(features.getOrDefault("piDecision", "UNKNOWN")),
                String.valueOf(features.getOrDefault("irbDecision", "UNKNOWN")),
                List.of(), c.problem(), c.outcome());
    }
}
