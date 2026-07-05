package io.casehub.clinical.demo;

import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.CriterionResult;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.service.AdverseEventService;
import io.casehub.clinical.service.EligibilityScreeningService;
import io.casehub.clinical.service.ProtocolAmendmentService;
import io.casehub.clinical.service.ProtocolDeviationService;
import io.casehub.clinical.service.TrialActivationService;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.TrustScoreJob;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.InboundHumanMessage;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Seeds a demo trial scenario on startup in dev mode.
 *
 * <p>Replays the ONCO-2024-001 trial scenario through real service calls
 * to produce Merkle-verified ledger entries and materialised trust scores.
 * Idempotent — skips entirely if the trial already exists.
 *
 * <p>Active only in dev profile with {@code casehub.clinical.demo.seed-data=true}.
 *
 * <p>The main {@code seed()} method is NOT {@code @Transactional} — each discrete
 * DB operation uses {@code @Transactional(REQUIRES_NEW)} helpers or
 * {@code QuarkusTransaction.requiringNew()} to avoid holding JDBC connections
 * during async polling.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DemoDataSeeder {

    private static final Logger LOG = Logger.getLogger(DemoDataSeeder.class);

    /** Deterministic UUID: {@code UUID.nameUUIDFromBytes("ONCO-2024-001".getBytes(UTF_8))} = 316e3846-4ea7-3b18-a6f7-e01ce6582a69. */
    public static final UUID TRIAL_ID =
            UUID.nameUUIDFromBytes("ONCO-2024-001".getBytes(StandardCharsets.UTF_8));

    // Deterministic site IDs for demo reproducibility
    static final UUID SITE_A_ID = UUID.nameUUIDFromBytes("SITE-A".getBytes(StandardCharsets.UTF_8));
    static final UUID SITE_B_ID = UUID.nameUUIDFromBytes("SITE-B".getBytes(StandardCharsets.UTF_8));
    static final UUID SITE_C_ID = UUID.nameUUIDFromBytes("SITE-C".getBytes(StandardCharsets.UTF_8));

    // Patient enrollment IDs — deterministic for demo stability
    static final UUID PATIENT_A1_ID = UUID.nameUUIDFromBytes("PATIENT-A-001".getBytes(StandardCharsets.UTF_8));
    static final UUID PATIENT_B1_ID = UUID.nameUUIDFromBytes("PATIENT-B-001".getBytes(StandardCharsets.UTF_8));
    static final UUID PATIENT_C1_ID = UUID.nameUUIDFromBytes("PATIENT-C-001".getBytes(StandardCharsets.UTF_8));

    /** Ledger entries use "default" tenantId — documented in CLAUDE.md ecosystem conventions. */
    private static final String LEDGER_TENANT_ID = "default";

    private static final long POLL_TIMEOUT_MS = 30_000;
    private static final long POLL_INTERVAL_MS = 500;

    @ConfigProperty(name = "casehub.clinical.demo.seed-data", defaultValue = "false")
    boolean seedEnabled;

    @Inject CurrentPrincipal principal;
    @Inject AdverseEventService adverseEventService;
    @Inject ProtocolDeviationService protocolDeviationService;
    @Inject EligibilityScreeningService eligibilityScreeningService;
    @Inject ProtocolAmendmentService protocolAmendmentService;
    @Inject TrialActivationService trialActivationService;
    @Inject ChannelGateway channelGateway;
    @Inject ChannelService channelService;
    @Inject WorkItemService workItemService;
    @Inject WorkItemStore workItemStore;
    @Inject TrustScoreJob trustScoreJob;
    @Inject LedgerVerificationService ledgerVerificationService;
    @Inject LedgerEntryRepository ledgerEntryRepository;

    void onStartup(@Observes StartupEvent event) {
        if (!seedEnabled) {
            LOG.info("Demo data seeding disabled (casehub.clinical.demo.seed-data=false)");
            return;
        }
        boolean exists = QuarkusTransaction.requiringNew().call(() ->
                ClinicalTrial.find("protocolId", "ONCO-2024-001").firstResult() != null);
        if (exists) {
            LOG.info("Demo trial ONCO-2024-001 already exists — skipping seed");
            return;
        }
        LOG.info("Seeding demo data for ONCO-2024-001...");
        try {
            seed();
            LOG.info("Demo data seeding complete");
        } catch (Exception e) {
            LOG.error("Demo data seeding failed — startup continues but demo may be incomplete", e);
        }
    }

    /**
     * Main seed orchestration — NOT @Transactional.
     * Each phase uses separate transactions to avoid holding JDBC connections
     * during async polling.
     */
    void seed() {
        // Phase 1: Trial structure
        createTrialAndSites();
        createPatients();

        // Phase 2: Activate trial (three-phase engine pattern)
        trialActivationService.activate(TRIAL_ID);
        LOG.info("Phase 1 complete: trial created and activated");

        // Phase 3: Site A — eligibility screening + AE lifecycle
        screenPatientA();
        reportGrade2Ae();
        seedSusarLifecycles();
        LOG.info("Phase 3 complete: Site A events seeded");

        // Phase 4: Site B — protocol deviation with PI approval
        seedProtocolDeviation();
        LOG.info("Phase 4 complete: Site B deviation seeded");

        // Phase 5: Site C — protocol amendment
        seedProtocolAmendment();
        LOG.info("Phase 5 complete: Site C amendment seeded");

        // Phase 6: Materialise trust scores
        materialiseTrustScores();
        LOG.info("Phase 6 complete: trust scores materialised");

        // Phase 7: Verify Merkle chains
        verifyMerkleChains();
        LOG.info("Phase 7 complete: Merkle chains verified");
    }

    // ── Phase 1: Trial structure ─────────────────────────────────────────────

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void createTrialAndSites() {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = TRIAL_ID;
        trial.protocolId = "ONCO-2024-001";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "Meridian Oncology Research";
        trial.targetEnrollment = 300;
        trial.status = TrialStatus.PLANNING;
        trial.tenantId = principal.tenancyId();
        trial.persist();

        addSite(SITE_A_ID, "dr-chen", 120);
        addSite(SITE_B_ID, "dr-martinez", 100);
        addSite(SITE_C_ID, "dr-okonkwo", 80);
    }

    private void addSite(UUID siteId, String investigatorId, int targetEnrollment) {
        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = TRIAL_ID;
        site.investigatorId = investigatorId;
        site.targetEnrollment = targetEnrollment;
        site.tenantId = principal.tenancyId();
        site.persist();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void createPatients() {
        addPatient(PATIENT_A1_ID, SITE_A_ID, "PATIENT-A-001");
        addPatient(PATIENT_B1_ID, SITE_B_ID, "PATIENT-B-001");
        addPatient(PATIENT_C1_ID, SITE_C_ID, "PATIENT-C-001");
    }

    private void addPatient(UUID enrollmentId, UUID siteId, String patientId) {
        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = enrollmentId;
        enrollment.siteId = siteId;
        enrollment.patientId = patientId;
        enrollment.consentStatus = ConsentStatus.OBTAINED;
        enrollment.enrolledAt = Instant.now();
        enrollment.tenantId = principal.tenancyId();
        enrollment.persist();
    }

    // ── Phase 3: Site A — screening + AEs ────────────────────────────────────

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void screenPatientA() {
        PatientEnrollment enrollment = PatientEnrollment.findById(PATIENT_A1_ID);
        List<CriterionResult> criteria = List.of(
                new CriterionResult("age-18-plus", true, false),
                new CriterionResult("ecog-0-2", true, false),
                new CriterionResult("prior-chemo", true, false));
        eligibilityScreeningService.screen(enrollment, criteria);
        LOG.info("Patient A-001 screened: CRITERIA_MET");
    }

    void reportGrade2Ae() {
        UUID aeId = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AdverseEvent ae = new AdverseEvent();
            ae.id = aeId;
            ae.enrollmentId = PATIENT_A1_ID;
            ae.grade = CtcaeGrade.GRADE_2;
            ae.actuality = EventActuality.ACTUAL;
            ae.outcome = AeOutcome.RESOLVING;
            ae.occurredAt = Instant.now().minus(Duration.ofDays(2));
            ae.unexpected = false;
            ae.suspected = true;
            ae.eventType = "NAUSEA";
            adverseEventService.reportAdverseEvent(ae);
        });
        LOG.infof("Grade 2 AE reported: %s (no SUSAR)", aeId);
    }

    /**
     * Seeds 3 Grade 4 unexpected AEs at Site A, each triggering a full SUSAR
     * oversight lifecycle. For each AE:
     *   1. Report the AE (triggers async SUSAR oversight case via CDI event)
     *   2. Poll for SUSAR oversight case to start (susarOversightCaseId != null)
     *   3. Poll for gate WorkItem to appear
     *   4. Complete the gate WorkItem (produces trust attestation)
     *   5. Poll for SUSAR oversight to complete
     *
     * AE escalation WorkItems are NOT completed — they remain active
     * to prime the DSMB rollup for the live demo (Step 5).
     */
    void seedSusarLifecycles() {
        for (int i = 1; i <= 3; i++) {
            UUID aeId = seedSingleSusarLifecycle(i);
            LOG.infof("SUSAR lifecycle %d/3 complete: aeId=%s", i, aeId);
        }
    }

    private UUID seedSingleSusarLifecycle(int index) {
        UUID aeId = UUID.randomUUID();

        // Step 1: Report Grade 4 unexpected AE (triggers SUSAR via CDI async event)
        QuarkusTransaction.requiringNew().run(() -> {
            AdverseEvent ae = new AdverseEvent();
            ae.id = aeId;
            ae.enrollmentId = PATIENT_A1_ID;
            ae.grade = CtcaeGrade.GRADE_4;
            ae.actuality = EventActuality.ACTUAL;
            ae.outcome = AeOutcome.ONGOING;
            ae.occurredAt = Instant.now().minus(Duration.ofHours(index));
            ae.unexpected = true;  // Required for SUSAR criteria
            ae.suspected = true;   // Required for SUSAR criteria
            ae.eventType = index == 1 ? "THROMBOCYTOPENIA" : index == 2 ? "FEBRILE_NEUTROPENIA" : "HEPATOTOXICITY";
            adverseEventService.reportAdverseEvent(ae);
        });
        LOG.infof("SUSAR %d: Grade 4 AE reported, aeId=%s", index, aeId);

        // Step 2: Poll for SUSAR oversight case to start
        pollUntil("SUSAR oversight case start for aeId=" + aeId, () ->
                QuarkusTransaction.requiringNew().call(() -> {
                    AdverseEvent ae = AdverseEvent.findById(aeId);
                    return ae != null && ae.susarOversightCaseId != null;
                }));

        UUID susarCaseId = QuarkusTransaction.requiringNew().call(() -> {
            AdverseEvent ae = AdverseEvent.findById(aeId);
            return ae.susarOversightCaseId;
        });
        LOG.infof("SUSAR %d: oversight case started, caseId=%s", index, susarCaseId);

        // Step 3: Poll for gate WorkItem to appear
        String caseIdStr = susarCaseId.toString();
        pollUntil("gate WorkItem for SUSAR case " + caseIdStr, () ->
                QuarkusTransaction.requiringNew().call(() ->
                        workItemStore.scanAll().stream()
                                .anyMatch(wi -> wi.callerRef != null
                                        && wi.callerRef.contains("case:" + caseIdStr))));

        // Step 4: Complete the gate WorkItem
        QuarkusTransaction.requiringNew().run(() -> {
            WorkItem gateWorkItem = workItemStore.scanAll().stream()
                    .filter(wi -> wi.callerRef != null && wi.callerRef.contains("case:" + caseIdStr))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Gate WorkItem not found for SUSAR case " + caseIdStr));

            String resolution = "{\"decision\":\"APPROVED\",\"approvedBy\":\"demo-investigator\"}";
            workItemService.completeFromSystem(gateWorkItem.id, "demo-investigator", resolution);
        });
        LOG.infof("SUSAR %d: gate WorkItem completed", index);

        // Step 5: Poll for SUSAR oversight to complete
        pollUntil("SUSAR oversight completion for aeId=" + aeId, () ->
                QuarkusTransaction.requiringNew().call(() -> {
                    AdverseEvent ae = AdverseEvent.findById(aeId);
                    return ae != null
                            && ae.susarOversightStatus == SusarOversightStatus.COMPLETED;
                }));

        return aeId;
    }

    // ── Phase 4: Site B — protocol deviation ─────────────────────────────────

    void seedProtocolDeviation() {
        UUID deviationId = UUID.randomUUID();

        // Step 1: Report CRITICAL deviation (triggers PI COMMAND via ProtocolDeviationService)
        QuarkusTransaction.requiringNew().run(() -> {
            ProtocolDeviation deviation = new ProtocolDeviation();
            deviation.id = deviationId;
            deviation.siteId = SITE_B_ID;
            deviation.deviationType = "inclusion-violation";
            deviation.severity = DeviationSeverity.CRITICAL;
            deviation.tenantId = principal.tenancyId();
            protocolDeviationService.reportDeviation(deviation);
        });
        LOG.infof("CRITICAL deviation reported: %s", deviationId);

        // Step 2: Poll for COMMANDED state (channel created, COMMAND message sent)
        pollUntil("deviation COMMANDED state for " + deviationId, () ->
                QuarkusTransaction.requiringNew().call(() -> {
                    ProtocolDeviation dev = ProtocolDeviation.findById(deviationId);
                    return dev != null
                            && dev.piApprovalStatus == PiApprovalStatus.COMMANDED;
                }));

        // Step 3: Approve as PI via channel gateway
        QuarkusTransaction.requiringNew().run(() -> {
            ProtocolDeviation dev = ProtocolDeviation.findById(deviationId);
            var channel = channelService.findByName(dev.piCommandChannelName)
                    .orElseThrow(() -> new IllegalStateException(
                            "PI oversight channel not found: " + dev.piCommandChannelName));

            channelGateway.receiveHumanMessage(
                    new ChannelRef(channel.id(), channel.name()),
                    new InboundHumanMessage(
                            "demo-pi",
                            "{\"decision\":\"APPROVED\"}",
                            Instant.now(),
                            Map.of(),
                            deviationId.toString(),
                            null));
        });
        LOG.info("PI approval message sent for deviation");

        // Step 4: Poll for PI approval to be processed
        pollUntil("PI approval for deviation " + deviationId, () ->
                QuarkusTransaction.requiringNew().call(() -> {
                    ProtocolDeviation dev = ProtocolDeviation.findById(deviationId);
                    return dev != null
                            && (dev.piApprovalStatus == PiApprovalStatus.APPROVED
                                || dev.piApprovalStatus == PiApprovalStatus.ESCALATED);
                }));
        LOG.info("PI approval processed for deviation");
    }

    // ── Phase 5: Site C — protocol amendment ─────────────────────────────────

    void seedProtocolAmendment() {
        protocolAmendmentService.propose(
                TRIAL_ID,
                "Dose escalation cohort expansion: increase maximum tolerated dose from 400mg to 600mg "
                        + "based on interim safety analysis showing acceptable Grade 1-2 toxicity profile",
                principal.tenancyId());
        LOG.info("Protocol amendment proposed for trial");
    }

    // ── Phase 6: Trust score materialisation ─────────────────────────────────

    void materialiseTrustScores() {
        try {
            trustScoreJob.runComputation();
            LOG.info("Bayesian Beta trust scores materialised from SUSAR attestations");
        } catch (Exception e) {
            LOG.warn("Trust score materialisation failed (non-fatal) — scores will compute on next scheduled run", e);
        }
    }

    // ── Phase 7: Merkle verification ─────────────────────────────────────────

    void verifyMerkleChains() {
        // Verify each enrollment that has ledger entries
        List<UUID> subjects = List.of(PATIENT_A1_ID, PATIENT_B1_ID, PATIENT_C1_ID);
        int verified = 0;

        for (UUID subjectId : subjects) {
            boolean hasEntries = QuarkusTransaction.requiringNew().call(() ->
                    !ledgerEntryRepository.findBySubjectId(subjectId, LEDGER_TENANT_ID).isEmpty());
            if (hasEntries) {
                boolean valid = QuarkusTransaction.requiringNew().call(() ->
                        ledgerVerificationService.verify(subjectId, LEDGER_TENANT_ID));
                if (!valid) {
                    throw new IllegalStateException(
                            "Merkle chain verification failed for subject " + subjectId
                                    + " — seeded data is corrupted");
                }
                verified++;
            }
        }
        LOG.infof("Merkle chains verified for %d subjects", verified);
    }

    // ── Polling utility ──────────────────────────────────────────────────────

    /**
     * Polls until the condition returns true, or throws after timeout.
     * Each poll runs in a fresh transaction via the supplied callable.
     * The calling method must NOT be @Transactional — this avoids holding
     * JDBC connections during the polling loop.
     */
    private void pollUntil(String description, Callable<Boolean> condition) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (Boolean.TRUE.equals(condition.call())) {
                    return;
                }
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while polling for: " + description, e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Error polling for: " + description, e);
            }
        }
        throw new IllegalStateException("Timed out after " + POLL_TIMEOUT_MS
                + "ms waiting for: " + description);
    }
}
