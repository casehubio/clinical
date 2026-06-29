package io.casehub.clinical.resource;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.clinical.api.ClinicalCapabilities;
import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.ClinicalTrustDimensions;
import io.casehub.clinical.entity.*;
import io.casehub.clinical.routing.ClinicalTrustRoutingPolicyProvider;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.ledger.repository.CaseLedgerEntryRepository;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
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
import java.util.HashMap;

@Path("/trials/{trialId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class TrialDashboardResource {

    @Inject CurrentPrincipal principal;
    @Inject ActorTrustScoreRepository trustScoreRepository;
    @Inject ClinicalTrustRoutingPolicyProvider trustRoutingPolicyProvider;
    @Inject CaseLedgerEntryRepository caseLedgerEntryRepository;
    @Inject LedgerEntryRepository ledgerEntryRepository;

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
        UUID id, UUID enrollmentId, UUID siteId, String grade, String type,
        Instant reportedAt, Instant slaDeadline, String escalationStatus,
        String regulatorySubmissionStatus, String slaTimeRemaining
    ) {}

    public record DeviationRow(
        UUID id, UUID siteId, String deviationType, String severity,
        String piApprovalStatus, Instant commandedAt
    ) {}

    public record AgentRow(
        String capability, String trustDimension,
        Double trustScore, Double threshold, int maturityPhase,
        int decisionCount, int attestationPositive, int attestationNegative
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
        Instant occurredAt, String summary
    ) {}

    public record SiteRow(UUID id, String investigatorId, String status,
                          long enrolledCount, long adverseEventCount,
                          long deviationCount) {}

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

        return Response.ok(List.of(new TrialSummary(
            trial.protocolId, trial.phase.name(), trial.sponsor,
            trial.targetEnrollment, enrolled, aeCount, devCount
        ))).build();
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

        List<AdverseEvent> aes = AdverseEvent
            .find("enrollmentId in ?1 and tenantId = ?2", enrollmentIds, principal.tenancyId())
            .list();

        Instant now = Instant.now();
        List<AdverseEventRow> rows = aes.stream().map(ae -> {
            String slaRemaining = null;
            if (ae.slaDeadline != null) {
                Duration remaining = Duration.between(now, ae.slaDeadline);
                if (remaining.isNegative()) {
                    slaRemaining = "OVERDUE by " + formatDuration(remaining.abs());
                } else {
                    slaRemaining = formatDuration(remaining) + " remaining";
                }
            }
            return new AdverseEventRow(
                ae.id, ae.enrollmentId, enrollmentToSite.get(ae.enrollmentId),
                ae.grade != null ? ae.grade.name() : null, null,
                ae.reportedAt, ae.slaDeadline,
                ae.escalationStatus != null ? ae.escalationStatus.name() : null,
                ae.regulatorySubmissionStatus != null ? ae.regulatorySubmissionStatus.name() : null,
                slaRemaining
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

        List<ProtocolDeviation> devs = ProtocolDeviation
            .find("siteId in ?1 and tenantId = ?2", siteIds, principal.tenancyId())
            .list();

        List<DeviationRow> rows = devs.stream().map(d -> new DeviationRow(
            d.id, d.siteId, d.deviationType,
            d.severity != null ? d.severity.name() : null,
            d.piApprovalStatus != null ? d.piApprovalStatus.name() : null,
            d.commandedAt
        )).toList();

        return Response.ok(rows).build();
    }

    @GET
    @Path("/agents")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response agents(@PathParam("trialId") UUID trialId) {
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null) return Response.status(404).build();

        List<AgentRow> rows = CAPABILITY_DIMENSIONS.stream().map(cd -> {
            String capability = cd[0];
            String dimension = cd[1];
            TrustRoutingPolicy policy = trustRoutingPolicyProvider.forCapability(capability);

            // Look up capability-level trust score (any actorId — aggregate view)
            List<ActorTrustScore> scores = trustScoreRepository.findAll().stream()
                .filter(s -> capability.equals(s.capabilityKey))
                .toList();

            if (scores.isEmpty()) {
                // Bootstrap phase — no trust data yet
                return new AgentRow(capability, dimension, null,
                    policy.threshold(), 0, 0, 0, 0);
            }

            // Aggregate across all actors for this capability
            double avgScore = scores.stream().mapToDouble(s -> s.trustScore).average().orElse(0.0);
            int totalDecisions = scores.stream().mapToInt(s -> s.decisionCount).sum();
            int totalPositive = scores.stream().mapToInt(s -> s.attestationPositive).sum();
            int totalNegative = scores.stream().mapToInt(s -> s.attestationNegative).sum();
            int maturity = totalDecisions >= policy.minimumObservations() ? 2 : 0;

            return new AgentRow(capability, dimension, avgScore,
                policy.threshold(), maturity, totalDecisions, totalPositive, totalNegative);
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

        if (ae.susarOversightCaseId != null) {
            // Query WorkerDecisionEntry for the safety-monitoring decision
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

                // Look up current trust score for this worker
                if (workerId != null) {
                    currentTrustScore = trustScoreRepository
                        .findCapabilityScore(workerId, ClinicalCapabilities.SAFETY_MONITORING)
                        .map(s -> s.trustScore)
                        .orElse(null);
                }
            }
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
            devBySite.getOrDefault(s.id, 0L)
        )).toList();

        return Response.ok(rows).build();
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
