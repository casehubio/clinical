package io.casehub.clinical.cbr;

import io.casehub.clinical.api.IrbApprovalResolvedEvent;
import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.*;
import io.casehub.clinical.entity.IrbApproval;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link DeviationResolutionCbrWriter}.
 * Persists entities, calls the writer, verifies CBR storage via memory store query.
 */
@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class DeviationResolutionCbrWriterIntegrationTest {

    @Inject
    DeviationResolutionCbrWriter writer;

    @Inject
    CbrCaseMemoryStore memoryStore;

    @Inject
    FixedCurrentPrincipal principal;

    @BeforeEach
    @TestTransaction
    void setup() {
        // Clean up any existing data
        ProtocolDeviation.deleteAll();
        IrbApproval.deleteAll();

        // Clean up in-memory CBR store (no exposed clear method, but can work around via domain isolation)
        // Note: InMemoryCbrStore doesn't expose clear(), so rely on tenantId isolation
    }

    @Test
    @TestTransaction
    void onProtocolDeviationResolved_StoresRetrievablePlanCase() {
        // Given: persisted MINOR deviation, PI APPROVED
        UUID deviationId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID engineCaseId = UUID.randomUUID();
        String tenantId = principal.tenancyId();

        var deviation = new ProtocolDeviation();
        deviation.id = deviationId;
        deviation.tenantId = tenantId;
        deviation.siteId = siteId;
        deviation.deviationType = "CONSENT_TIMING_DELAY";
        deviation.severity = DeviationSeverity.MINOR;
        deviation.escalationRequirement = EscalationRequirement.NONE;
        deviation.piApprovalStatus = PiApprovalStatus.APPROVED;
        deviation.engineCaseId = engineCaseId;
        deviation.persist();

        var event = new ProtocolDeviationResolvedEvent(
            deviationId,
            siteId,
            DeviationSeverity.MINOR,
            EscalationRequirement.NONE,
            PiApprovalStatus.APPROVED,
            "CONSENT_TIMING_DELAY",
            "pi-smith",
            tenantId
        );

        // When: writer processes event
        writer.onProtocolDeviationResolved(event);

        // Then: CBR case is retrievable
        var query = CbrQuery.of(
            tenantId,
            ClinicalCbrDomains.DEVIATION,
            "clinical-deviation",
            Map.of("deviationType", "CONSENT_TIMING_DELAY", "severity", "MINOR"),
            10
        );

        List<ScoredCbrCase<PlanCbrCase>> results = memoryStore.retrieveSimilar(query, PlanCbrCase.class);
        assertThat(results).isNotEmpty();

        PlanCbrCase retrieved = results.get(0).cbrCase();
        assertThat(retrieved.problem()).contains("CONSENT_TIMING_DELAY", "MINOR");
        assertThat(retrieved.solution()).contains("PI decision: APPROVED");
        assertThat(retrieved.outcome()).isEqualTo("RESOLVED");
        assertThat(retrieved.features())
            .containsEntry("deviationType", "CONSENT_TIMING_DELAY")
            .containsEntry("severity", "MINOR")
            .containsEntry("piDecision", "APPROVED")
            .containsEntry("irbDecision", "N/A");

        assertThat(retrieved.planTrace()).hasSize(1);
        assertThat(retrieved.planTrace().get(0).bindingName()).isEqualTo("pi-oversight");
        assertThat(retrieved.planTrace().get(0).stepOutcome()).isEqualTo("APPROVED");
    }

    @Test
    @TestTransaction
    void onIrbApprovalResolved_OverwritesExistingCase() {
        // Given: persisted CRITICAL deviation with PI ESCALATED and IRB APPROVED
        UUID deviationId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID engineCaseId = UUID.randomUUID();
        String tenantId = principal.tenancyId();

        var deviation = new ProtocolDeviation();
        deviation.id = deviationId;
        deviation.tenantId = tenantId;
        deviation.siteId = siteId;
        deviation.deviationType = "INFORMED_CONSENT_VIOLATION";
        deviation.severity = DeviationSeverity.CRITICAL;
        deviation.escalationRequirement = EscalationRequirement.IRB_REVIEW;
        deviation.piApprovalStatus = PiApprovalStatus.ESCALATED;
        deviation.engineCaseId = engineCaseId;
        deviation.persist();

        var irbApproval = new IrbApproval();
        irbApproval.id = approvalId;
        irbApproval.tenantId = tenantId;
        irbApproval.siteId = siteId;
        irbApproval.deviationId = deviationId;
        irbApproval.deviationType = "INFORMED_CONSENT_VIOLATION";
        irbApproval.reviewType = "FULL_BOARD";
        irbApproval.committeeId = "irb-001";
        irbApproval.decisionDeadline = Instant.now().plusSeconds(72 * 3600);
        irbApproval.decision = IrbDecision.APPROVED;
        irbApproval.persist();

        // First store: PI decision only
        var piEvent = new ProtocolDeviationResolvedEvent(
            deviationId,
            siteId,
            DeviationSeverity.CRITICAL,
            EscalationRequirement.IRB_REVIEW,
            PiApprovalStatus.ESCALATED,
            "INFORMED_CONSENT_VIOLATION",
            "pi-jones",
            tenantId
        );
        writer.onProtocolDeviationResolved(piEvent);

        // When: IRB decision arrives and overwrites
        var irbEvent = new IrbApprovalResolvedEvent(
            approvalId,
            deviationId,
            siteId,
            IrbDecision.APPROVED,
            Instant.now(),
            tenantId
        );
        writer.onIrbApprovalResolved(irbEvent);

        // Then: CBR case now includes both PI and IRB decisions
        var query = CbrQuery.of(
            tenantId,
            ClinicalCbrDomains.DEVIATION,
            "clinical-deviation",
            Map.of("deviationType", "INFORMED_CONSENT_VIOLATION"),
            10
        );

        List<ScoredCbrCase<PlanCbrCase>> results = memoryStore.retrieveSimilar(query, PlanCbrCase.class);
        assertThat(results).isNotEmpty();

        // Find the case for this specific deviation
        PlanCbrCase retrieved = results.stream()
            .map(ScoredCbrCase::cbrCase)
            .filter(c -> c.features().get("deviationType").equals("INFORMED_CONSENT_VIOLATION"))
            .findFirst()
            .orElseThrow();
        assertThat(retrieved.solution()).contains("IRB decision: APPROVED");
        assertThat(retrieved.features())
            .containsEntry("piDecision", "ESCALATED")
            .containsEntry("irbDecision", "APPROVED");

        assertThat(retrieved.planTrace()).hasSize(2);
        assertThat(retrieved.planTrace().get(0).stepOutcome()).isEqualTo("ESCALATED");
        assertThat(retrieved.planTrace().get(1).stepOutcome()).isEqualTo("APPROVED");
        assertThat(retrieved.planTrace().get(1).bindingName()).isEqualTo("irb-committee");
    }

    @Test
    @TestTransaction
    void onProtocolDeviationResolved_MajorDeviationRejected_StoresPlanCase() {
        // Given: MAJOR deviation rejected by PI (no IRB, no sponsor notification)
        UUID deviationId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        String tenantId = principal.tenancyId();

        var deviation = new ProtocolDeviation();
        deviation.id = deviationId;
        deviation.tenantId = tenantId;
        deviation.siteId = siteId;
        deviation.deviationType = "PROCEDURE_SEQUENCE_ERROR";
        deviation.severity = DeviationSeverity.MAJOR;
        deviation.escalationRequirement = EscalationRequirement.SPONSOR_NOTIFICATION;
        deviation.piApprovalStatus = PiApprovalStatus.REJECTED;
        deviation.engineCaseId = UUID.randomUUID();
        deviation.persist();

        var event = new ProtocolDeviationResolvedEvent(
            deviationId,
            siteId,
            DeviationSeverity.MAJOR,
            EscalationRequirement.SPONSOR_NOTIFICATION,
            PiApprovalStatus.REJECTED,
            "PROCEDURE_SEQUENCE_ERROR",
            "pi-brown",
            tenantId
        );

        // When: writer processes event
        writer.onProtocolDeviationResolved(event);

        // Then: CBR case is stored with REJECTED decision
        var query = CbrQuery.of(
            tenantId,
            ClinicalCbrDomains.DEVIATION,
            "clinical-deviation",
            Map.of("severity", "MAJOR"),
            10
        );

        List<ScoredCbrCase<PlanCbrCase>> results = memoryStore.retrieveSimilar(query, PlanCbrCase.class);
        assertThat(results).isNotEmpty();

        PlanCbrCase retrieved = results.get(0).cbrCase();
        assertThat(retrieved.features())
            .containsEntry("piDecision", "REJECTED")
            .containsEntry("severity", "MAJOR")
            .containsEntry("escalationRequirement", "SPONSOR_NOTIFICATION");
    }
}
