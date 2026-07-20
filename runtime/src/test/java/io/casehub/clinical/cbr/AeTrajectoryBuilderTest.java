package io.casehub.clinical.cbr;

import io.casehub.api.model.TaskStatus;
import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.engine.common.internal.model.PlanItemRecord;
import io.casehub.engine.common.spi.PlanItemStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AeTrajectoryBuilderTest {

    private PlanItemStore planItemStore;
    private AeTrajectoryBuilder builder;

    @BeforeEach
    void setUp() {
        planItemStore = mock(PlanItemStore.class);
        builder = new AeTrajectoryBuilder(planItemStore);
    }

    @Test
    void noEngineCases_singleObservationFromEntity() {
        AdverseEvent ae = buildAe(CtcaeGrade.GRADE_1, null, null, null);
        var trajectory = builder.buildTrajectory(ae, "tenant-1");

        assertEquals(1, trajectory.size());
        var obs = trajectory.get(0);
        assertEquals(0.0, ((FeatureValue.NumberVal) obs.get("ts")).value());
        assertEquals(0.0, ((FeatureValue.NumberVal) obs.get("escalation")).value());
        assertEquals(0.0, ((FeatureValue.NumberVal) obs.get("susar")).value());
        assertEquals(0.0, ((FeatureValue.NumberVal) obs.get("regulatory")).value());
    }

    @Test
    void withEngineCaseOnly_buildsTrajectoryFromPlanItems() {
        UUID caseId = UUID.randomUUID();
        AdverseEvent ae = buildAe(CtcaeGrade.GRADE_3, caseId, null, null);
        ae.escalationStatus = AeEscalationStatus.COMPLETED;

        Instant base = ae.reportedAt;
        when(planItemStore.findByCaseId(caseId, "tenant-1")).thenReturn(List.of(
            planItem(caseId, "safety-review", TaskStatus.COMPLETED, base.plusSeconds(3600)),
            planItem(caseId, "dsmb-escalation", TaskStatus.COMPLETED, base.plusSeconds(7200))
        ));

        var trajectory = builder.buildTrajectory(ae, "tenant-1");

        assertTrue(trajectory.size() >= 3);
        assertEquals(0.0, ((FeatureValue.NumberVal) trajectory.get(0).get("ts")).value());
        double prevTs = 0;
        for (int i = 1; i < trajectory.size(); i++) {
            double ts = ((FeatureValue.NumberVal) trajectory.get(i).get("ts")).value();
            assertTrue(ts >= prevTs, "Observations must be sorted by timestamp");
            prevTs = ts;
        }
    }

    @Test
    void multipleEngineCases_mergesAndSorts() {
        UUID escalationCaseId = UUID.randomUUID();
        UUID susarCaseId = UUID.randomUUID();
        UUID regCaseId = UUID.randomUUID();
        AdverseEvent ae = buildAe(CtcaeGrade.GRADE_4, escalationCaseId, susarCaseId, regCaseId);
        ae.escalationStatus = AeEscalationStatus.COMPLETED;
        ae.susarOversightStatus = SusarOversightStatus.COMPLETED;
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.PENDING;

        Instant base = ae.reportedAt;
        when(planItemStore.findByCaseId(escalationCaseId, "tenant-1")).thenReturn(List.of(
            planItem(escalationCaseId, "safety-review", TaskStatus.COMPLETED, base.plusSeconds(1800))
        ));
        when(planItemStore.findByCaseId(susarCaseId, "tenant-1")).thenReturn(List.of(
            planItem(susarCaseId, "susar-assessment", TaskStatus.COMPLETED, base.plusSeconds(3600))
        ));
        when(planItemStore.findByCaseId(regCaseId, "tenant-1")).thenReturn(List.of(
            planItem(regCaseId, "ind-submission", TaskStatus.PENDING, base.plusSeconds(5400))
        ));

        var trajectory = builder.buildTrajectory(ae, "tenant-1");

        assertTrue(trajectory.size() >= 4);
        for (var obs : trajectory) {
            assertNotNull(obs.get("ts"));
            assertNotNull(obs.get("escalation"));
            assertNotNull(obs.get("susar"));
            assertNotNull(obs.get("regulatory"));
        }
    }

    @Test
    void partialTrajectory_sameAsFullForDevelopingCase() {
        UUID caseId = UUID.randomUUID();
        AdverseEvent ae = buildAe(CtcaeGrade.GRADE_3, caseId, null, null);
        ae.escalationStatus = AeEscalationStatus.REQUESTED;

        when(planItemStore.findByCaseId(caseId, "tenant-1")).thenReturn(List.of(
            planItem(caseId, "safety-review", TaskStatus.PENDING, ae.reportedAt.plusSeconds(600))
        ));

        var full = builder.buildTrajectory(ae, "tenant-1");
        var partial = builder.buildPartialTrajectory(ae, "tenant-1");
        assertEquals(full, partial);
    }

    @Test
    void timestampsAreRelativeToReportedAt() {
        UUID caseId = UUID.randomUUID();
        AdverseEvent ae = buildAe(CtcaeGrade.GRADE_3, caseId, null, null);
        ae.escalationStatus = AeEscalationStatus.COMPLETED;

        when(planItemStore.findByCaseId(caseId, "tenant-1")).thenReturn(List.of(
            planItem(caseId, "safety-review", TaskStatus.COMPLETED, ae.reportedAt.plusSeconds(7200))
        ));

        var trajectory = builder.buildTrajectory(ae, "tenant-1");
        assertEquals(0.0, ((FeatureValue.NumberVal) trajectory.get(0).get("ts")).value());
        assertEquals(7200.0, ((FeatureValue.NumberVal) trajectory.get(1).get("ts")).value());
    }

    @Test
    void susarBinding_setsRequestedThenCompleted() {
        UUID caseId = UUID.randomUUID();
        UUID susarCaseId = UUID.randomUUID();
        AdverseEvent ae = buildAe(CtcaeGrade.GRADE_4, caseId, susarCaseId, null);
        ae.escalationStatus = AeEscalationStatus.COMPLETED;
        ae.susarOversightStatus = SusarOversightStatus.COMPLETED;

        when(planItemStore.findByCaseId(caseId, "tenant-1")).thenReturn(List.of(
            planItem(caseId, "safety-review", TaskStatus.COMPLETED, ae.reportedAt.plusSeconds(1800))
        ));
        when(planItemStore.findByCaseId(susarCaseId, "tenant-1")).thenReturn(List.of(
            planItem(susarCaseId, "susar-assessment", TaskStatus.COMPLETED, ae.reportedAt.plusSeconds(3600))
        ));

        var trajectory = builder.buildTrajectory(ae, "tenant-1");

        var lastObs = trajectory.get(trajectory.size() - 1);
        assertEquals(2.0, ((FeatureValue.NumberVal) lastObs.get("susar")).value());
    }

    private AdverseEvent buildAe(CtcaeGrade grade, UUID engineCaseId, UUID susarCaseId, UUID regCaseId) {
        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.grade = grade;
        ae.reportedAt = Instant.parse("2026-01-15T10:00:00Z");
        ae.enrollmentId = UUID.randomUUID();
        ae.engineCaseId = engineCaseId;
        ae.susarOversightCaseId = susarCaseId;
        ae.regulatorySubmissionCaseId = regCaseId;
        ae.escalationStatus = AeEscalationStatus.NONE;
        ae.susarOversightStatus = SusarOversightStatus.NONE;
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.NONE;
        ae.tenantId = "tenant-1";
        return ae;
    }

    private PlanItemRecord planItem(UUID caseId, String binding, TaskStatus status, Instant createdAt) {
        return new PlanItemRecord(caseId, UUID.randomUUID().toString(), binding, status, createdAt,
            null, null, "tenant-1", null, null, null);
    }
}
