package io.casehub.clinical.cbr;

import io.casehub.api.model.TaskStatus;
import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.clinical.api.model.*;
import io.casehub.clinical.entity.*;
import io.casehub.engine.common.internal.model.PlanItemRecord;
import io.casehub.engine.common.internal.model.TargetType;
import io.casehub.engine.common.spi.PlanItemStore;
import io.casehub.neocortex.memory.cbr.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ClinicalCaseOutcomeObserverTest {

    private ClinicalCbrService cbrService;
    private CbrCaseMemoryStore store;
    private PlanItemStore planItemStore;
    private ClinicalScopeResolver scopeResolver;
    private ClinicalCaseOutcomeObserver observer;

    @BeforeEach
    void setUp() {
        cbrService = mock(ClinicalCbrService.class);
        store = mock(CbrCaseMemoryStore.class);
        planItemStore = mock(PlanItemStore.class);
        scopeResolver = mock(ClinicalScopeResolver.class);
        when(scopeResolver.forAdverseEvent(any())).thenReturn(java.util.Optional.of(io.casehub.platform.api.path.Path.of("trial-1", "site-1", "patient-1")));
        AeTrajectoryBuilder trajectoryBuilder = mock(AeTrajectoryBuilder.class);
        observer = new ClinicalCaseOutcomeObserver(cbrService, store, planItemStore, trajectoryBuilder, scopeResolver);
    }

    @Test
    void onOutcome_aeCase_storesPlanCbrCaseWithPlanTrace() {
        UUID aeId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.grade = CtcaeGrade.GRADE_3;
        ae.eventType = "Neutropenia";
        ae.suspected = true;
        ae.unexpected = true;
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.FILED;
        ae.susarOversightStatus = SusarOversightStatus.COMPLETED;
        ae.tenantId = "test-tenant";
        ae.engineCaseId = caseId;

        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.treatmentArm = "ARM_B";

        ClinicalTrial trial = new ClinicalTrial();
        trial.phase = TrialPhase.PHASE_III;

        observer.setEntityResolver(new TestEntityResolver(ae, enrollment, trial, 2));

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("aeId", aeId.toString());
        snapshot.put("safetyReview", "CONTINUE_MONITORING");
        snapshot.put("dsmbEscalation", "true");

        List<PlanItemRecord> planItems = List.of(
            PlanItemRecord.primitive(caseId, "pi-1", "safety-review", TaskStatus.COMPLETED,
                Instant.now(), TargetType.HUMAN_TASK, null, "test-tenant", null, "officer-alpha", null)
        );
        when(planItemStore.findByCaseId(caseId, "test-tenant")).thenReturn(planItems);

        CaseOutcomeEvent event = new CaseOutcomeEvent(
            "ae-escalation", "test-tenant", caseId, snapshot, "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        ArgumentCaptor<CbrCase> caseCaptor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrService).storeIdempotent(
            caseCaptor.capture(), eq("clinical-ae"), eq(aeId.toString()),
            eq(ClinicalCbrDomains.AE), eq("test-tenant"), eq(caseId.toString()), any());

        CbrCase stored = caseCaptor.getValue();
        assertThat(stored).isInstanceOf(PlanCbrCase.class);
        PlanCbrCase plan = (PlanCbrCase) stored;
        assertThat(plan.features()).hasSize(11);
        assertThat(plan.planTrace()).hasSize(1);
        assertThat(plan.planTrace().get(0).bindingName()).isEqualTo("safety-review");
        assertThat(plan.planTrace().get(0).capabilityName()).isEqualTo("safety-monitoring");
        assertThat(plan.planTrace().get(0).workerName()).isEqualTo("officer-alpha");
        assertThat(plan.planTrace().get(0).stepOutcome()).isEqualTo("COMPLETED");
    }

    @Test
    void onOutcome_aeCase_alsoRecordsOutcome() {
        UUID aeId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.grade = CtcaeGrade.GRADE_1;
        ae.tenantId = "test-tenant";
        ae.engineCaseId = caseId;
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.NONE;
        ae.susarOversightStatus = SusarOversightStatus.NONE;

        observer.setEntityResolver(new TestEntityResolver(ae, null, null, 0));
        when(planItemStore.findByCaseId(caseId, "test-tenant")).thenReturn(List.of());

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("aeId", aeId.toString());

        CaseOutcomeEvent event = new CaseOutcomeEvent(
            "ae-escalation", "test-tenant", caseId, snapshot, "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        verify(store).recordOutcome(eq(aeId.toString()), eq("test-tenant"), any(CbrOutcome.class));
    }

    @Test
    void onOutcome_deviationCase_onlyRecordsOutcome() {
        UUID caseId = UUID.randomUUID();
        UUID deviationId = UUID.randomUUID();

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("deviationId", deviationId.toString());

        CaseOutcomeEvent event = new CaseOutcomeEvent(
            "protocol-deviation", "test-tenant", caseId, snapshot, "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        verify(cbrService, never()).storeIdempotent(any(), any(), any(), any(), any(), any(), any());
        verify(store).recordOutcome(eq(deviationId.toString()), eq("test-tenant"), any(CbrOutcome.class));
    }

    @Test
    void onOutcome_amendmentCase_onlyRecordsOutcome() {
        UUID caseId = UUID.randomUUID();
        UUID amendmentId = UUID.randomUUID();

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("amendmentId", amendmentId.toString());

        CaseOutcomeEvent event = new CaseOutcomeEvent(
            "protocol-amendment", "test-tenant", caseId, snapshot, "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        verify(cbrService, never()).storeIdempotent(any(), any(), any(), any(), any(), any(), any());
        verify(store).recordOutcome(eq(amendmentId.toString()), eq("test-tenant"), any(CbrOutcome.class));
    }

    @Test
    void onOutcome_unknownCaseType_logsAndSkips() {
        UUID caseId = UUID.randomUUID();
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("someOtherKey", "value");

        CaseOutcomeEvent event = new CaseOutcomeEvent(
            "unknown-case", "test-tenant", caseId, snapshot, "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        verify(cbrService, never()).storeIdempotent(any(), any(), any(), any(), any(), any(), any());
        verify(store, never()).recordOutcome(any(), any(), any());
    }

    @Test
    void onOutcome_aeNotFound_skipsStorageButRecordsOutcome() {
        UUID aeId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        observer.setEntityResolver(new TestEntityResolver(null, null, null, 0));

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("aeId", aeId.toString());

        CaseOutcomeEvent event = new CaseOutcomeEvent(
            "ae-escalation", "test-tenant", caseId, snapshot, "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        verify(cbrService, never()).storeIdempotent(any(), any(), any(), any(), any(), any(), any());
        verify(store).recordOutcome(eq(aeId.toString()), eq("test-tenant"), any(CbrOutcome.class));
    }

    @Test
    void onOutcome_noPlanItemsWithExecutor_storesEmptyTrace() {
        UUID aeId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.grade = CtcaeGrade.GRADE_3;
        ae.eventType = "Headache";
        ae.tenantId = "test-tenant";
        ae.engineCaseId = caseId;
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.NONE;
        ae.susarOversightStatus = SusarOversightStatus.NONE;

        observer.setEntityResolver(new TestEntityResolver(ae, null, null, 0));
        when(planItemStore.findByCaseId(caseId, "test-tenant")).thenReturn(List.of());

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("aeId", aeId.toString());

        CaseOutcomeEvent event = new CaseOutcomeEvent(
            "ae-escalation", "test-tenant", caseId, snapshot, "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        ArgumentCaptor<CbrCase> caseCaptor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrService).storeIdempotent(caseCaptor.capture(), any(), any(), any(), any(), any(), any());
        PlanCbrCase plan = (PlanCbrCase) caseCaptor.getValue();
        assertThat(plan.planTrace()).isEmpty();
    }

    @Test
    void onOutcome_unmappedBinding_skippedInTrace() {
        UUID aeId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.grade = CtcaeGrade.GRADE_3;
        ae.eventType = "Rash";
        ae.tenantId = "test-tenant";
        ae.engineCaseId = caseId;
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.NONE;
        ae.susarOversightStatus = SusarOversightStatus.NONE;

        observer.setEntityResolver(new TestEntityResolver(ae, null, null, 0));

        List<PlanItemRecord> planItems = List.of(
            PlanItemRecord.primitive(caseId, "pi-1", "unknown-binding", TaskStatus.COMPLETED,
                Instant.now(), TargetType.HUMAN_TASK, null, "test-tenant", null, "worker-x", null),
            PlanItemRecord.primitive(caseId, "pi-2", "safety-review", TaskStatus.COMPLETED,
                Instant.now(), TargetType.HUMAN_TASK, null, "test-tenant", null, "officer-beta", null)
        );
        when(planItemStore.findByCaseId(caseId, "test-tenant")).thenReturn(planItems);

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("aeId", aeId.toString());

        CaseOutcomeEvent event = new CaseOutcomeEvent(
            "ae-escalation", "test-tenant", caseId, snapshot, "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        ArgumentCaptor<CbrCase> caseCaptor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrService).storeIdempotent(caseCaptor.capture(), any(), any(), any(), any(), any(), any());
        PlanCbrCase plan = (PlanCbrCase) caseCaptor.getValue();
        assertThat(plan.planTrace()).hasSize(1);
        assertThat(plan.planTrace().get(0).bindingName()).isEqualTo("safety-review");
    }

    @Test
    void onOutcome_faultedOutcome_recordsLowConfidence() {
        UUID aeId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        observer.setEntityResolver(new TestEntityResolver(null, null, null, 0));

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("aeId", aeId.toString());

        CaseOutcomeEvent event = new CaseOutcomeEvent(
            "ae-escalation", "test-tenant", caseId, snapshot, "FAULTED", Instant.now(), Map.of());

        observer.onOutcome(event);

        ArgumentCaptor<CbrOutcome> outcomeCaptor = ArgumentCaptor.forClass(CbrOutcome.class);
        verify(store).recordOutcome(eq(aeId.toString()), eq("test-tenant"), outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().successRate()).isEqualTo(0.0);
    }

    @Test
    void onOutcome_completedOutcome_recordsHighConfidence() {
        UUID deviationId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("deviationId", deviationId.toString());

        CaseOutcomeEvent event = new CaseOutcomeEvent(
            "protocol-deviation", "test-tenant", caseId, snapshot, "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        ArgumentCaptor<CbrOutcome> outcomeCaptor = ArgumentCaptor.forClass(CbrOutcome.class);
        verify(store).recordOutcome(eq(deviationId.toString()), eq("test-tenant"), outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().successRate()).isEqualTo(1.0);
    }

    record TestEntityResolver(
        AdverseEvent ae, PatientEnrollment enrollment,
        ClinicalTrial trial, long priorAeCount
    ) implements ClinicalCaseOutcomeObserver.EntityResolver {
        @Override public AdverseEvent findAe(UUID aeId) { return ae; }
        @Override public PatientEnrollment findEnrollment(UUID id) { return enrollment; }
        @Override public TrialSite findSite(UUID id) { return null; }
        @Override public ClinicalTrial findTrial(UUID id) { return trial; }
        @Override public long countPriorAes(UUID enrollmentId, UUID excludeAeId) { return priorAeCount; }
    }
}
