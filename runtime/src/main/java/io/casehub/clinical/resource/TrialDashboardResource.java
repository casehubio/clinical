package io.casehub.clinical.resource;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.clinical.api.ClinicalCapabilities;
import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.ClinicalTrustDimensions;
import io.casehub.clinical.api.model.*;
import io.casehub.clinical.cbr.ClinicalCbrDomains;
import io.casehub.clinical.cbr.ClinicalCbrService;
import io.casehub.clinical.entity.*;
import io.casehub.clinical.routing.ClinicalTrustRoutingPolicyProvider;
import io.casehub.neocortex.memory.cbr.*;
// TODO: removed in ledger SNAPSHOT — needs equivalent
// import io.casehub.ledger.model.WorkerDecisionEntry;
// import io.casehub.ledger.repository.CaseLedgerEntryRepository;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/trials/{trialId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class TrialDashboardResource {

    @Inject CurrentPrincipal principal;
    @Inject ActorTrustScoreRepository trustScoreRepository;
    @Inject ClinicalTrustRoutingPolicyProvider trustRoutingPolicyProvider;
    // TODO: removed in ledger SNAPSHOT — needs equivalent
    // @Inject CaseLedgerEntryRepository caseLedgerEntryRepository;
    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject ClinicalCbrService cbrService;

    // --- Response records (nested per clinical convention) ---

    public record TrialSummary(
        String protocolId, String phase, String sponsor, int targetEnrollment,
        long totalEnrolled, long totalAdverseEvents, long totalDeviations
    ) {}

    public record PatientRow(
        UUID id, UUID siteId, String patientId, String enrollmentStatus,
        String screeningResult, String consentStatus
    ) {}

    public record AdverseEventRow(
        UUID id, UUID enrollmentId, UUID siteId, String siteName,
        String patientId, String grade, String eventType,
        Instant reportedAt, Instant slaDeadline, String escalationStatus,
        String regulatorySubmissionStatus, String slaTimeRemaining,
        Double slaTimeRemainingHours
    ) {}

    public record DeviationRow(
        UUID id, UUID siteId, String siteName, String deviationType,
        String severity, String piApprovalStatus,
        Instant reportedAt, String irbDecision
    ) {}

    public record AgentRow(
        String capability, String trustDimension,
        Double trustScore, Double threshold, String maturityPhase,
        int decisionCount, int attestationPositive, int attestationNegative,
        Double endorsementRatio, String distinctTrustDimensions
    ) {}

    public record GovernanceContext(
        String grade, boolean unexpected, boolean suspected,
        String susarOversightStatus,
        String workerId, String capabilityTag,
        Double trustScoreAtRouting, Double thresholdApplied,
        Double currentTrustScore,
        String gateStatus
    ) {}

    public record LedgerEntryRow(
        UUID id, UUID subjectId, int sequenceNumber,
        String entryType, String actorId, String actorRole,
        Instant occurredAt, String digest, String summary
    ) {}

    public record SiteRow(UUID id, String investigatorId, String status,
                          long enrolledCount, long adverseEventCount,
                          long deviationCount, int targetEnrollment) {}

    public record CommitmentLifecycleResponse(
        UUID deviationId,
        String deviationType,
        String severity,
        String piApprovalStatus,
        String channelName,
        Instant commandedAt,
        Instant resolvedAt
    ) {}

    /** All 8 capabilities with their primary trust dimension. */
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

    // --- Endpoints ---

    @GET
    @Path("/summary")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response summary(@PathParam("trialId") UUID trialId) {
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null) return Response.status(404).build();

        List<TrialSite> sites = TrialSite.find("trialId = ?1 and tenantId = ?2",
            trialId, principal.tenancyId()).list();

        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();

        long enrolled = siteIds.isEmpty() ? 0 :
            PatientEnrollment.count("siteId in ?1 and tenantId = ?2",
                siteIds, principal.tenancyId());

        long aeCount = 0;
        if (!siteIds.isEmpty()) {
            List<PatientEnrollment> enrollments = PatientEnrollment
                .find("siteId in ?1 and tenantId = ?2", siteIds, principal.tenancyId())
                .list();
            List<UUID> enrollmentIds = enrollments.stream().map(e -> e.id).toList();
            if (!enrollmentIds.isEmpty()) {
                aeCount = AdverseEvent.count("enrollmentId in ?1 and tenantId = ?2",
                    enrollmentIds, principal.tenancyId());
            }
        }

        long devCount = siteIds.isEmpty() ? 0 :
            ProtocolDeviation.count("siteId in ?1 and tenantId = ?2",
                siteIds, principal.tenancyId());

        return Response.ok(new TrialSummary(
            trial.protocolId, trial.phase.name(), trial.sponsor,
            trial.targetEnrollment, enrolled, aeCount, devCount
        )).build();
    }

    @GET
    @Path("/patients")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response patients(@PathParam("trialId") UUID trialId) {
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null) return Response.status(404).build();

        List<TrialSite> sites = TrialSite.find("trialId = ?1 and tenantId = ?2",
            trialId, principal.tenancyId()).list();
        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();

        if (siteIds.isEmpty()) return Response.ok(List.of()).build();

        List<PatientEnrollment> enrollments = PatientEnrollment
            .find("siteId in ?1 and tenantId = ?2", siteIds, principal.tenancyId())
            .list();

        List<PatientRow> rows = enrollments.stream().map(e -> new PatientRow(
            e.id, e.siteId, e.patientId,
            e.enrollmentStatus != null ? e.enrollmentStatus.name() : null,
            e.screeningResult != null ? e.screeningResult.name() : null,
            e.consentStatus != null ? e.consentStatus.name() : null
        )).toList();

        return Response.ok(rows).build();
    }

    @GET
    @Path("/adverse-events")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response adverseEvents(@PathParam("trialId") UUID trialId) {
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null) return Response.status(404).build();

        // Two-hop: trial → sites → enrollments → AEs
        List<TrialSite> sites = TrialSite.find("trialId = ?1 and tenantId = ?2",
            trialId, principal.tenancyId()).list();
        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();
        if (siteIds.isEmpty()) return Response.ok(List.of()).build();

        List<PatientEnrollment> enrollments = PatientEnrollment
            .find("siteId in ?1 and tenantId = ?2", siteIds, principal.tenancyId())
            .list();
        if (enrollments.isEmpty()) return Response.ok(List.of()).build();

        List<UUID> enrollmentIds = enrollments.stream().map(e -> e.id).toList();
        // Build siteId lookup from enrollment
        Map<UUID, UUID> enrollmentToSite = enrollments.stream()
            .collect(Collectors.toMap(e -> e.id, e -> e.siteId));
        Map<UUID, String> siteIdToName = sites.stream()
            .collect(Collectors.toMap(s -> s.id, s -> s.investigatorId));
        Map<UUID, String> enrollmentToPatientId = enrollments.stream()
            .collect(Collectors.toMap(e -> e.id, e -> e.patientId));

        List<AdverseEvent> aes = AdverseEvent
            .find("enrollmentId in ?1 and tenantId = ?2", enrollmentIds, principal.tenancyId())
            .list();

        Instant now = Instant.now();
        List<AdverseEventRow> rows = aes.stream().map(ae -> {
            String slaRemaining = null;
            Double slaHours = null;
            if (ae.slaDeadline != null) {
                Duration remaining = Duration.between(now, ae.slaDeadline);
                slaHours = remaining.toMinutes() / 60.0;
                if (remaining.isNegative()) {
                    slaRemaining = "OVERDUE by " + formatDuration(remaining.abs());
                } else {
                    slaRemaining = formatDuration(remaining) + " remaining";
                }
            }
            return new AdverseEventRow(
                ae.id, ae.enrollmentId, enrollmentToSite.get(ae.enrollmentId),
                siteIdToName.get(enrollmentToSite.get(ae.enrollmentId)),
                enrollmentToPatientId.get(ae.enrollmentId),
                ae.grade != null ? ae.grade.name() : null,
                ae.eventType,
                ae.reportedAt, ae.slaDeadline,
                ae.escalationStatus != null ? ae.escalationStatus.name() : null,
                ae.regulatorySubmissionStatus != null ? ae.regulatorySubmissionStatus.name() : null,
                slaRemaining,
                slaHours
            );
        }).toList();

        return Response.ok(rows).build();
    }

    @GET
    @Path("/deviations")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response deviations(@PathParam("trialId") UUID trialId) {
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null) return Response.status(404).build();

        List<TrialSite> sites = TrialSite.find("trialId = ?1 and tenantId = ?2",
            trialId, principal.tenancyId()).list();
        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();
        if (siteIds.isEmpty()) return Response.ok(List.of()).build();

        Map<UUID, String> siteIdToName = sites.stream()
            .collect(Collectors.toMap(s -> s.id, s -> s.investigatorId));

        List<ProtocolDeviation> devs = ProtocolDeviation
            .find("siteId in ?1 and tenantId = ?2", siteIds, principal.tenancyId())
            .list();

        List<UUID> devIds = devs.stream().map(d -> d.id).toList();
        Map<UUID, String> irbDecisionByDeviation = devIds.isEmpty() ? Map.of() :
            IrbApproval.<IrbApproval>find("deviationId in ?1 and tenantId = ?2", devIds, principal.tenancyId())
                .stream()
                .collect(Collectors.toMap(irb -> irb.deviationId, irb -> irb.decision.name(), (a, b) -> a));

        List<DeviationRow> rows = devs.stream().map(d -> {
            String irbDecision = irbDecisionByDeviation.get(d.id);
            return new DeviationRow(
                d.id, d.siteId, siteIdToName.get(d.siteId), d.deviationType,
                d.severity != null ? d.severity.name() : null,
                d.piApprovalStatus != null ? d.piApprovalStatus.name() : null,
                d.commandedAt, irbDecision
            );
        }).toList();

        return Response.ok(rows).build();
    }

    @GET
    @Path("/agents")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response agents(@PathParam("trialId") UUID trialId) {
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null) return Response.status(404).build();

        String distinctDimensions = CAPABILITY_DIMENSIONS.stream()
            .map(pair -> pair[1])
            .distinct()
            .sorted()
            .collect(Collectors.joining(", "));

        List<AgentRow> rows = CAPABILITY_DIMENSIONS.stream().map(cd -> {
            String capability = cd[0];
            String dimension = cd[1];
            TrustRoutingPolicy policy = trustRoutingPolicyProvider.forCapability(capability);

            List<ActorTrustScore> scores = trustScoreRepository.findAll().stream()
                .filter(s -> capability.equals(s.capabilityKey))
                .toList();

            if (scores.isEmpty()) {
                return new AgentRow(capability, dimension, null,
                    policy.threshold(), "bootstrap", 0, 0, 0, null, distinctDimensions);
            }

            double avgScore = scores.stream().mapToDouble(s -> s.trustScore).average().orElse(0.0);
            int totalDecisions = scores.stream().mapToInt(s -> s.decisionCount).sum();
            int totalPositive = scores.stream().mapToInt(s -> s.attestationPositive).sum();
            int totalNegative = scores.stream().mapToInt(s -> s.attestationNegative).sum();
            String maturity;
            if (totalDecisions < 10) maturity = "bootstrap";
            else if (totalDecisions < 50) maturity = "emerging";
            else maturity = "established";
            int totalAttestations = totalPositive + totalNegative;
            Double endorsementRatio = totalAttestations == 0
                ? null
                : (double) totalPositive / totalAttestations;

            return new AgentRow(capability, dimension, avgScore,
                policy.threshold(), maturity, totalDecisions, totalPositive, totalNegative,
                endorsementRatio, distinctDimensions);
        }).toList();

        return Response.ok(rows).build();
    }

    @GET
    @Path("/adverse-events/{aeId}/governance")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response governance(@PathParam("trialId") UUID trialId,
                               @PathParam("aeId") UUID aeId) {
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null) return Response.status(404).build();

        AdverseEvent ae = AdverseEvent.findByIdForTenant(aeId, principal);
        if (ae == null) return Response.status(404).build();

        // Verify AE belongs to this trial (via enrollment → site → trial)
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(ae.enrollmentId, principal);
        if (enrollment == null) return Response.status(404).build();
        TrialSite site = TrialSite.findByIdForTenant(enrollment.siteId, principal);
        if (site == null || !site.trialId.equals(trialId)) return Response.status(404).build();

        // Default values for when no SUSAR oversight case exists
        String workerId = null;
        String capabilityTag = null;
        Double trustScoreAtRouting = null;
        Double thresholdApplied = null;
        Double currentTrustScore = null;
        String gateStatus = ae.susarOversightStatus.name();

        // TODO: removed in ledger SNAPSHOT — WorkerDecisionEntry and CaseLedgerEntryRepository need equivalents
        // Stubbed for now to get build green
        if (ae.susarOversightCaseId != null) {
            // Query WorkerDecisionEntry for the safety-monitoring decision
            // List<WorkerDecisionEntry> decisions =
            //     caseLedgerEntryRepository.findWorkerDecisionsByCaseId(ae.susarOversightCaseId);
            // Optional<WorkerDecisionEntry> safetyDecision = decisions.stream()
            //     .filter(e -> ClinicalCapabilities.SAFETY_MONITORING.equals(e.capabilityTag))
            //     .findFirst();
            //
            // if (safetyDecision.isPresent()) {
            //     WorkerDecisionEntry entry = safetyDecision.get();
            //     workerId = entry.workerId;
            //     capabilityTag = entry.capabilityTag;
            //     trustScoreAtRouting = entry.trustScoreAtRouting;
            //     thresholdApplied = entry.thresholdApplied;
            //
            //     // Look up current trust score for this worker
            //     if (workerId != null) {
            //         currentTrustScore = trustScoreRepository
            //             .findCapabilityScore(workerId, ClinicalCapabilities.SAFETY_MONITORING)
            //             .map(s -> s.trustScore)
            //             .orElse(null);
            //     }
            // }
        }

        return Response.ok(new GovernanceContext(
            ae.grade != null ? ae.grade.name() : null,
            ae.unexpected, ae.suspected,
            ae.susarOversightStatus.name(),
            workerId, capabilityTag,
            trustScoreAtRouting, thresholdApplied,
            currentTrustScore, gateStatus
        )).build();
    }

    @GET
    @Path("/ledger-entries")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response ledgerEntries(@PathParam("trialId") UUID trialId,
                                  @QueryParam("type") String typeFilter) {
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null) return Response.status(404).build();

        // Collect subject IDs from default datasource: enrollment IDs + deviation IDs
        List<TrialSite> sites = TrialSite.find("trialId = ?1 and tenantId = ?2",
            trialId, principal.tenancyId()).list();
        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();
        if (siteIds.isEmpty()) return Response.ok(List.of()).build();

        // Enrollment IDs
        List<PatientEnrollment> enrollments = PatientEnrollment
            .find("siteId in ?1 and tenantId = ?2", siteIds, principal.tenancyId())
            .list();
        List<UUID> enrollmentIds = enrollments.stream().map(e -> e.id).toList();

        // Deviation IDs
        List<ProtocolDeviation> deviations = ProtocolDeviation
            .find("siteId in ?1 and tenantId = ?2", siteIds, principal.tenancyId())
            .list();
        List<UUID> deviationIds = deviations.stream().map(d -> d.id).toList();

        // Also include AE IDs as subjects (AE-related ledger entries use AE ID as subjectId)
        List<UUID> aeIds = List.of();
        if (!enrollmentIds.isEmpty()) {
            List<AdverseEvent> aes = AdverseEvent
                .find("enrollmentId in ?1 and tenantId = ?2", enrollmentIds, principal.tenancyId())
                .list();
            aeIds = aes.stream().map(ae -> ae.id).toList();
        }

        // Combine all subject IDs
        List<UUID> allSubjectIds = Stream.of(enrollmentIds.stream(), deviationIds.stream(), aeIds.stream())
            .flatMap(s -> s)
            .toList();

        if (allSubjectIds.isEmpty()) return Response.ok(List.of()).build();

        // Query ledger entries from qhorus datasource for each subject ID
        List<LedgerEntryRow> rows = allSubjectIds.stream()
            .flatMap(subjectId -> ledgerEntryRepository.findBySubjectId(subjectId, "default").stream())
            .filter(entry -> typeFilter == null || typeFilter.isEmpty()
                || entry.entryType.name().equalsIgnoreCase(typeFilter))
            .sorted(Comparator.comparing(entry -> entry.occurredAt != null ? entry.occurredAt : Instant.EPOCH))
            .map(entry -> new LedgerEntryRow(
                entry.id, entry.subjectId, entry.sequenceNumber,
                entry.entryType != null ? entry.entryType.name() : null,
                entry.actorId, entry.actorRole,
                entry.occurredAt,
                entry.digest,
                buildLedgerSummary(entry)
            ))
            .toList();

        return Response.ok(rows).build();
    }

    @GET
    @Path("/sites")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response sites(@PathParam("trialId") UUID trialId) {
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null) return Response.status(404).build();

        List<TrialSite> sites = TrialSite.find("trialId = ?1 and tenantId = ?2",
            trialId, principal.tenancyId()).list();

        if (sites.isEmpty()) return Response.ok(List.of()).build();

        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();

        // Enrollments per site (single-hop)
        List<PatientEnrollment> enrollments = PatientEnrollment
            .find("siteId in ?1 and tenantId = ?2", siteIds, principal.tenancyId())
            .list();
        Map<UUID, Long> enrolledBySite = enrollments.stream()
            .collect(Collectors.groupingBy(e -> e.siteId, Collectors.counting()));

        // AEs per site (two-hop: enrollment → AE, grouped back to site)
        Map<UUID, Long> aeBySite = new HashMap<>();
        if (!enrollments.isEmpty()) {
            List<UUID> enrollmentIds = enrollments.stream().map(e -> e.id).toList();
            Map<UUID, UUID> enrollmentToSite = enrollments.stream()
                .collect(Collectors.toMap(e -> e.id, e -> e.siteId));
            List<AdverseEvent> aes = AdverseEvent
                .find("enrollmentId in ?1 and tenantId = ?2", enrollmentIds, principal.tenancyId())
                .list();
            aeBySite = aes.stream()
                .collect(Collectors.groupingBy(
                    ae -> enrollmentToSite.get(ae.enrollmentId), Collectors.counting()));
        }

        // Deviations per site (single-hop)
        Map<UUID, Long> devBySite = ProtocolDeviation
            .find("siteId in ?1 and tenantId = ?2", siteIds, principal.tenancyId())
            .<ProtocolDeviation>list().stream()
            .collect(Collectors.groupingBy(d -> d.siteId, Collectors.counting()));

        Map<UUID, Long> finalAeBySite = aeBySite;
        List<SiteRow> rows = sites.stream().map(s -> new SiteRow(
            s.id, s.investigatorId,
            s.status != null ? s.status.name() : null,
            enrolledBySite.getOrDefault(s.id, 0L),
            finalAeBySite.getOrDefault(s.id, 0L),
            devBySite.getOrDefault(s.id, 0L),
            s.targetEnrollment
        )).toList();

        return Response.ok(rows).build();
    }

    @GET
    @Path("/adverse-events/{aeId}/precedents")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response aePrecedents(@PathParam("trialId") UUID trialId,
                                  @PathParam("aeId") UUID aeId) {
        // Load AE
        AdverseEvent ae = AdverseEvent.findByIdForTenant(aeId, principal);
        if (ae == null) return Response.status(404).build();

        // Resolve trial via enrollment → site → trial traversal
        PatientEnrollment enrollment = ae.enrollmentId != null
            ? PatientEnrollment.findByIdForTenant(ae.enrollmentId, principal)
            : null;
        TrialSite site = enrollment != null && enrollment.siteId != null
            ? TrialSite.findByIdForTenant(enrollment.siteId, principal)
            : null;
        ClinicalTrial trial = site != null && site.trialId != null
            ? ClinicalTrial.findByIdForTenant(site.trialId, principal)
            : null;

        // Build features map from AE (categorical fields must be Strings)
        Map<String, Object> features = Map.of(
            "grade", ae.grade != null ? ae.grade.ordinal() + 1 : 0,
            "eventType", ae.eventType != null ? ae.eventType : "UNKNOWN",
            "trialPhase", trial != null && trial.phase != null ? trial.phase.name() : "UNKNOWN",
            "unexpected", String.valueOf(ae.unexpected),
            "suspected", String.valueOf(ae.suspected)
        );

        // Build query with outcome features weighted 0.0
        CbrQuery query = CbrQuery.of(principal.tenancyId(), ClinicalCbrDomains.AE,
                "clinical-ae", features, 10)
            .withMinSimilarity(0.3)
            .withVectorWeight(0.0)
            .withWeight("safetyReviewOutcome", 0.0)
            .withWeight("dsmbEscalated", 0.0)
            .withWeight("indReportFiled", 0.0)
            .withWeight("susarOversight", 0.0);

        List<AePrecedentResponse> results = cbrService.retrieveSimilar(query, FeatureVectorCbrCase.class)
            .stream()
            .map(this::mapToAeResponse)
            .toList();

        return Response.ok(results).build();
    }

    @GET
    @Path("/deviations/{devId}/precedents")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response deviationPrecedents(@PathParam("trialId") UUID trialId,
                                        @PathParam("devId") UUID devId) {
        // Load deviation
        ProtocolDeviation deviation = ProtocolDeviation.findByIdForTenant(devId, principal);
        if (deviation == null) return Response.status(404).build();

        // Build features map
        Map<String, Object> features = Map.of(
            "deviationType", deviation.deviationType != null ? deviation.deviationType : "UNKNOWN",
            "severity", deviation.severity != null ? deviation.severity.name() : "UNKNOWN",
            "escalationRequirement", deviation.escalationRequirement != null
                ? deviation.escalationRequirement.name() : "UNKNOWN"
        );

        // Build query with outcome features weighted 0.0
        CbrQuery query = CbrQuery.of(principal.tenancyId(), ClinicalCbrDomains.DEVIATION,
                "clinical-deviation", features, 10)
            .withMinSimilarity(0.3)
            .withVectorWeight(0.0)
            .withWeight("piDecision", 0.0)
            .withWeight("irbDecision", 0.0);

        List<DeviationPrecedentResponse> results = cbrService.retrieveSimilar(query, PlanCbrCase.class)
            .stream()
            .map(this::mapToDeviationResponse)
            .toList();

        return Response.ok(results).build();
    }

    @GET
    @Path("/deviations/{devId}/commitment")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    public Response getCommitmentLifecycle(
            @PathParam("trialId") UUID trialId,
            @PathParam("devId") UUID devId) {

        ProtocolDeviation deviation = ProtocolDeviation.findByIdForTenant(devId, principal);
        if (deviation == null) {
            return Response.status(404).build();
        }

        // Verify deviation belongs to this trial (via site → trial)
        TrialSite site = TrialSite.findByIdForTenant(deviation.siteId, principal);
        if (site == null || !site.trialId.equals(trialId)) {
            return Response.status(404).build();
        }

        var response = new CommitmentLifecycleResponse(
            devId,
            deviation.deviationType,
            deviation.severity != null ? deviation.severity.name() : null,
            deviation.piApprovalStatus != null ? deviation.piApprovalStatus.name() : null,
            deviation.piCommandChannelName,
            deviation.commandedAt,
            deviation.responseDeadline
        );

        return Response.ok(response).build();
    }

    private AePrecedentResponse mapToAeResponse(ScoredCbrCase<FeatureVectorCbrCase> scored) {
        FeatureVectorCbrCase c = scored.cbrCase();
        Map<String, Object> features = c.features();

        // Extract features with safe casting
        Object gradeObj = features.get("grade");
        int gradeInt = gradeObj instanceof Number ? ((Number) gradeObj).intValue() : 0;
        String gradeStr = gradeInt > 0 && gradeInt <= 5 ? "GRADE_" + gradeInt : "UNKNOWN";

        // Boolean features are stored as Strings ("true"/"false")
        return new AePrecedentResponse(
            scored.score(),
            gradeStr,
            String.valueOf(features.getOrDefault("eventType", "UNKNOWN")),
            String.valueOf(features.getOrDefault("trialPhase", "UNKNOWN")),
            "true".equals(String.valueOf(features.get("unexpected"))),
            "true".equals(String.valueOf(features.get("suspected"))),
            String.valueOf(features.getOrDefault("safetyReviewOutcome", "UNKNOWN")),
            "true".equals(String.valueOf(features.get("dsmbEscalated"))),
            "true".equals(String.valueOf(features.get("indReportFiled"))),
            "true".equals(String.valueOf(features.get("susarOversight"))),
            c.problem(),
            c.outcome()
        );
    }

    private DeviationPrecedentResponse mapToDeviationResponse(ScoredCbrCase<PlanCbrCase> scored) {
        PlanCbrCase c = scored.cbrCase();
        Map<String, Object> features = c.features();

        // Map plan traces to step responses
        List<PlanStepResponse> steps = c.planTrace().stream()
            .map(trace -> new PlanStepResponse(
                trace.bindingName(),
                trace.capabilityName(),
                trace.stepOutcome()
            ))
            .toList();

        return new DeviationPrecedentResponse(
            scored.score(),
            String.valueOf(features.getOrDefault("deviationType", "UNKNOWN")),
            String.valueOf(features.getOrDefault("severity", "UNKNOWN")),
            String.valueOf(features.getOrDefault("escalationRequirement", "UNKNOWN")),
            String.valueOf(features.getOrDefault("piDecision", "UNKNOWN")),
            String.valueOf(features.getOrDefault("irbDecision", "UNKNOWN")),
            steps,
            c.problem(),
            c.outcome()
        );
    }

    /** Build a human-readable summary from ledger entry fields. */
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
}
