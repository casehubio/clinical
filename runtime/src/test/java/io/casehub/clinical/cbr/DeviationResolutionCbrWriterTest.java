package io.casehub.clinical.cbr;

import io.casehub.clinical.api.IrbApprovalResolvedEvent;
import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.*;
import io.casehub.clinical.entity.IrbApproval;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link DeviationResolutionCbrWriter} with mocked service.
 * Uses real Panache entities and persisted data, mocks only the CBR service.
 * Verifies event observation, entity loading, and CBR case construction.
 */
@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class DeviationResolutionCbrWriterTest {

    @Inject
    DeviationResolutionCbrWriter writer;

    @InjectMock
    ClinicalCbrService cbrService;

    @InjectMock
    ClinicalScopeResolver scopeResolver;

    @Inject
    FixedCurrentPrincipal principal;

    @BeforeEach
    @TestTransaction
    void setup() {
        // Clean entities and stub service
        ProtocolDeviation.deleteAll();
        IrbApproval.deleteAll();

        when(scopeResolver.forDeviation(any())).thenReturn(java.util.Optional.of(io.casehub.platform.api.path.Path.of("trial-1", "site-1")));
        when(cbrService.storeIdempotent(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn("mock-cbr-id");
    }

    @Test
    @TestTransaction
    void onProtocolDeviationResolved_MinorDeviation_StoresPlanCaseWithPiDecisionOnly() {
        // Given: MINOR deviation, PI APPROVED, no IRB involvement
        UUID deviationId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        String tenantId = principal.tenancyId();

        var deviation = new ProtocolDeviation();
        deviation.id = deviationId;
        deviation.tenantId = tenantId;
        deviation.siteId = siteId;
        deviation.deviationType = "CONSENT_TIMING_DELAY";
        deviation.severity = DeviationSeverity.MINOR;
        deviation.escalationRequirement = EscalationRequirement.NONE;
        deviation.piApprovalStatus = PiApprovalStatus.APPROVED;
        deviation.engineCaseId = UUID.randomUUID();
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

        // When: event is observed
        writer.onProtocolDeviationResolved(event);

        // Then: PlanCbrCase stored with 5 features and 1 plan trace
        ArgumentCaptor<PlanCbrCase> caseCaptor = ArgumentCaptor.forClass(PlanCbrCase.class);
        verify(cbrService).storeIdempotent(
            caseCaptor.capture(),
            eq("clinical-deviation"),
            eq(deviationId.toString()),
            eq(ClinicalCbrDomains.DEVIATION),
            eq(tenantId),
            eq(deviation.engineCaseId.toString()),
            any()
        );

        PlanCbrCase stored = caseCaptor.getValue();
        assertThat(stored.problem()).contains("CONSENT_TIMING_DELAY", "MINOR", "NONE");
        assertThat(stored.solution()).contains("PI decision: APPROVED");
        assertThat(stored.outcome()).isEqualTo("RESOLVED");
        assertThat(stored.confidence()).isEqualTo(1.0);

        Map<String, Object> features = FeatureValue.toRawMap(stored.features());
        assertThat(features)
            .containsEntry("deviationType", "CONSENT_TIMING_DELAY")
            .containsEntry("severity", "MINOR")
            .containsEntry("escalationRequirement", "NONE")
            .containsEntry("piDecision", "APPROVED")
            .containsEntry("irbDecision", "N/A");

        List<PlanTrace> trace = stored.planTrace();
        assertThat(trace).hasSize(1);
        assertThat(trace.get(0).bindingName()).isEqualTo("pi-oversight");
        assertThat(trace.get(0).capabilityName()).isEqualTo("pi-authorisation");
        assertThat(trace.get(0).stepOutcome()).isEqualTo("APPROVED");
        assertThat(trace.get(0).priority()).isEqualTo(1);
    }

    @Test
    @TestTransaction
    void onProtocolDeviationResolved_CriticalDeviation_StoresPlanCaseWithPendingIrbDecision() {
        // Given: CRITICAL deviation, PI ESCALATED, IRB still PENDING
        UUID deviationId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        String tenantId = principal.tenancyId();

        var deviation = new ProtocolDeviation();
        deviation.id = deviationId;
        deviation.tenantId = tenantId;
        deviation.siteId = siteId;
        deviation.deviationType = "INFORMED_CONSENT_VIOLATION";
        deviation.severity = DeviationSeverity.CRITICAL;
        deviation.escalationRequirement = EscalationRequirement.IRB_REVIEW;
        deviation.piApprovalStatus = PiApprovalStatus.ESCALATED;
        deviation.engineCaseId = UUID.randomUUID();
        deviation.persist();

        // IRB approval exists but decision is still PENDING
        var irbApproval = new IrbApproval();
        irbApproval.id = UUID.randomUUID();
        irbApproval.tenantId = tenantId;
        irbApproval.siteId = siteId;
        irbApproval.deviationId = deviationId;
        irbApproval.reviewType = "FULL_BOARD";
        irbApproval.committeeId = "irb-001";
        irbApproval.decisionDeadline = Instant.now().plusSeconds(72 * 3600);
        irbApproval.decision = IrbDecision.PENDING;
        irbApproval.persist();

        var event = new ProtocolDeviationResolvedEvent(
            deviationId,
            siteId,
            DeviationSeverity.CRITICAL,
            EscalationRequirement.IRB_REVIEW,
            PiApprovalStatus.ESCALATED,
            "INFORMED_CONSENT_VIOLATION",
            "pi-jones",
            tenantId
        );

        // When: event is observed
        writer.onProtocolDeviationResolved(event);

        // Then: PlanCbrCase stored with irbDecision = "PENDING"
        ArgumentCaptor<PlanCbrCase> caseCaptor = ArgumentCaptor.forClass(PlanCbrCase.class);
        verify(cbrService).storeIdempotent(
            caseCaptor.capture(),
            eq("clinical-deviation"),
            eq(deviationId.toString()),
            eq(ClinicalCbrDomains.DEVIATION),
            eq(tenantId),
            eq(deviation.engineCaseId.toString()),
            any()
        );

        PlanCbrCase stored = caseCaptor.getValue();
        assertThat(FeatureValue.toRawMap(stored.features()))
            .containsEntry("piDecision", "ESCALATED")
            .containsEntry("irbDecision", "N/A");  // No IRB decision yet

        List<PlanTrace> trace = stored.planTrace();
        assertThat(trace).hasSize(1);
        assertThat(trace.get(0).stepOutcome()).isEqualTo("ESCALATED");
    }

    @Test
    @TestTransaction
    void onIrbApprovalResolved_OverwritesExistingCase() {
        // Given: CRITICAL deviation already has PI decision, now IRB decides
        UUID deviationId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        String tenantId = principal.tenancyId();

        var deviation = new ProtocolDeviation();
        deviation.id = deviationId;
        deviation.tenantId = tenantId;
        deviation.siteId = siteId;
        deviation.deviationType = "INFORMED_CONSENT_VIOLATION";
        deviation.severity = DeviationSeverity.CRITICAL;
        deviation.escalationRequirement = EscalationRequirement.IRB_REVIEW;
        deviation.piApprovalStatus = PiApprovalStatus.ESCALATED;
        deviation.engineCaseId = UUID.randomUUID();
        deviation.persist();

        var irbApproval = new IrbApproval();
        irbApproval.id = approvalId;
        irbApproval.tenantId = tenantId;
        irbApproval.siteId = siteId;
        irbApproval.deviationId = deviationId;
        irbApproval.reviewType = "FULL_BOARD";
        irbApproval.committeeId = "irb-001";
        irbApproval.decisionDeadline = Instant.now().plusSeconds(72 * 3600);
        irbApproval.decision = IrbDecision.APPROVED;
        irbApproval.persist();

        var event = new IrbApprovalResolvedEvent(
            approvalId,
            deviationId,
            siteId,
            IrbDecision.APPROVED,
            Instant.now(),
            tenantId
        );

        // When: IRB event is observed
        writer.onIrbApprovalResolved(event);

        // Then: PlanCbrCase stored with both PI and IRB decisions
        ArgumentCaptor<PlanCbrCase> caseCaptor = ArgumentCaptor.forClass(PlanCbrCase.class);
        verify(cbrService).storeIdempotent(
            caseCaptor.capture(),
            eq("clinical-deviation"),
            eq(deviationId.toString()),
            eq(ClinicalCbrDomains.DEVIATION),
            eq(tenantId),
            eq(deviation.engineCaseId.toString()),
            any()
        );

        PlanCbrCase stored = caseCaptor.getValue();
        assertThat(stored.solution()).contains("IRB decision: APPROVED");
        assertThat(FeatureValue.toRawMap(stored.features()))
            .containsEntry("piDecision", "ESCALATED")
            .containsEntry("irbDecision", "APPROVED");

        List<PlanTrace> trace = stored.planTrace();
        assertThat(trace).hasSize(2);
        assertThat(trace.get(0).bindingName()).isEqualTo("pi-oversight");
        assertThat(trace.get(0).stepOutcome()).isEqualTo("ESCALATED");
        assertThat(trace.get(1).bindingName()).isEqualTo("irb-committee");
        assertThat(trace.get(1).capabilityName()).isEqualTo("irb-consultation");
        assertThat(trace.get(1).stepOutcome()).isEqualTo("APPROVED");
        assertThat(trace.get(1).priority()).isEqualTo(2);
    }

    @Test
    @TestTransaction
    void onProtocolDeviationResolved_DeviationNotFound_Logs() {
        // Given: event for non-existent deviation
        UUID deviationId = UUID.randomUUID();
        var event = new ProtocolDeviationResolvedEvent(
            deviationId,
            UUID.randomUUID(),
            DeviationSeverity.MINOR,
            EscalationRequirement.NONE,
            PiApprovalStatus.APPROVED,
            "UNKNOWN",
            "pi-smith",
            principal.tenancyId()
        );

        // When: event is observed
        writer.onProtocolDeviationResolved(event);

        // Then: no store called
        verify(cbrService, never()).storeIdempotent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @TestTransaction
    void onProtocolDeviationResolved_NoEngineCaseId_StoresWithNullCaseId() {
        // Given: deviation expired before case started
        UUID deviationId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        String tenantId = principal.tenancyId();

        var deviation = new ProtocolDeviation();
        deviation.id = deviationId;
        deviation.tenantId = tenantId;
        deviation.siteId = siteId;
        deviation.deviationType = "PROTOCOL_TIMING";
        deviation.severity = DeviationSeverity.MINOR;
        deviation.escalationRequirement = EscalationRequirement.NONE;
        deviation.piApprovalStatus = PiApprovalStatus.EXPIRED;
        deviation.engineCaseId = null;  // No case started
        deviation.persist();

        var event = new ProtocolDeviationResolvedEvent(
            deviationId,
            siteId,
            DeviationSeverity.MINOR,
            EscalationRequirement.NONE,
            PiApprovalStatus.EXPIRED,
            "PROTOCOL_TIMING",
            null,  // No PI when EXPIRED
            tenantId
        );

        // When: event is observed
        writer.onProtocolDeviationResolved(event);

        // Then: stored with null caseId
        verify(cbrService).storeIdempotent(
            any(PlanCbrCase.class),
            eq("clinical-deviation"),
            eq(deviationId.toString()),
            eq(ClinicalCbrDomains.DEVIATION),
            eq(tenantId),
            eq(null),  // No engine case ID
            any()
        );
    }
}
