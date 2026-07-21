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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AeTrajectoryBuilderTest {

    private PlanItemStore planItemStore;
    private AeTrajectoryBuilder builder;

    @BeforeEach
    void setUp() {
        planItemStore = mock(PlanItemStore.class);
        builder       = new AeTrajectoryBuilder(planItemStore);
        builder.setGradeHistoryFinder(id -> java.util.List.of());}

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

    @Test
    void duplicateTimestamps_coalesced_strictlyAscending() {
        UUID         caseId      = UUID.randomUUID();
        UUID         susarCaseId = UUID.randomUUID();
        AdverseEvent ae          = buildAe(CtcaeGrade.GRADE_4, caseId, susarCaseId, null);
        ae.escalationStatus     = AeEscalationStatus.COMPLETED;
        ae.susarOversightStatus = SusarOversightStatus.COMPLETED;

        Instant base = ae.reportedAt;
        // All records at the same timestamp — simulates fast engine processing
        when(planItemStore.findByCaseId(caseId, "tenant-1")).thenReturn(List.of(
                planItem(caseId, "safety-review", TaskStatus.COMPLETED, base)
                                                                               ));
        when(planItemStore.findByCaseId(susarCaseId, "tenant-1")).thenReturn(List.of(
                planItem(susarCaseId, "susar-assessment", TaskStatus.COMPLETED, base)
                                                                                    ));

        var trajectory = builder.buildTrajectory(ae, "tenant-1");

        // Must have strictly ascending timestamps — no duplicates
        for (int i = 1; i < trajectory.size(); i++) {
            double prev = ((FeatureValue.NumberVal) trajectory.get(i - 1).get("ts")).value();
            double curr = ((FeatureValue.NumberVal) trajectory.get(i).get("ts")).value();
            assertTrue(curr > prev, "Timestamps must be strictly ascending, got " + prev + " then " + curr);
        }
    }


    @Test
    void noGradeHistory_usesCurrentGrade() {
        AdverseEvent ae         = buildAe(CtcaeGrade.GRADE_2, null, null, null);
        var          trajectory = builder.buildTrajectory(ae, "tenant-1");
        assertEquals(1, trajectory.size());
        assertEquals(2.0, ((FeatureValue.NumberVal) trajectory.get(0).get("grade")).value());
    }

    @Test
    void withGradeHistory_firstObservationUsesInitialGrade() {
        AdverseEvent ae  = buildAe(CtcaeGrade.GRADE_3, null, null, null);
        var          gc1 = gradeChange(ae.id, null, CtcaeGrade.GRADE_1, ae.reportedAt);
        var gc2 = gradeChange(ae.id, CtcaeGrade.GRADE_1, CtcaeGrade.GRADE_3,
                              ae.reportedAt.plusSeconds(172800));
        builder.setGradeHistoryFinder(id -> List.of(gc1, gc2));

        var trajectory = builder.buildTrajectory(ae, "tenant-1");
        assertTrue(trajectory.size() >= 2);
        assertEquals(1.0, ((FeatureValue.NumberVal) trajectory.get(0).get("grade")).value());
        assertEquals(3.0, ((FeatureValue.NumberVal) trajectory.get(trajectory.size() - 1).get("grade")).value());
    }

    @Test
    void gradeChangeMergedWithPlanItems_sortedByTimestamp() {
        UUID         caseId = UUID.randomUUID();
        AdverseEvent ae     = buildAe(CtcaeGrade.GRADE_3, caseId, null, null);
        ae.escalationStatus = AeEscalationStatus.REQUESTED;

        var gc1 = gradeChange(ae.id, null, CtcaeGrade.GRADE_1, ae.reportedAt);
        var gc2 = gradeChange(ae.id, CtcaeGrade.GRADE_1, CtcaeGrade.GRADE_3,
                              ae.reportedAt.plusSeconds(7200));
        builder.setGradeHistoryFinder(id -> List.of(gc1, gc2));

        when(planItemStore.findByCaseId(caseId, "tenant-1"))
                .thenReturn(List.of(
                        planItem(caseId, "safety-review", TaskStatus.PENDING, ae.reportedAt.plusSeconds(3600))));

        var trajectory = builder.buildTrajectory(ae, "tenant-1");
        assertTrue(trajectory.size() >= 3);
        assertEquals(1.0, ((FeatureValue.NumberVal) trajectory.get(0).get("grade")).value());
        var secondObs = trajectory.get(1);
        assertEquals(3600.0, ((FeatureValue.NumberVal) secondObs.get("ts")).value());
        assertEquals(1.0, ((FeatureValue.NumberVal) secondObs.get("grade")).value());
        var thirdObs = trajectory.get(2);
        assertEquals(7200.0, ((FeatureValue.NumberVal) thirdObs.get("ts")).value());
        assertEquals(3.0, ((FeatureValue.NumberVal) thirdObs.get("grade")).value());
    }

    private io.casehub.clinical.entity.AeGradeChange gradeChange(UUID aeId, CtcaeGrade prev,
                                                                 CtcaeGrade next, Instant at) {
        var gc = new io.casehub.clinical.entity.AeGradeChange();
        gc.id             = UUID.randomUUID();
        gc.adverseEventId = aeId;
        gc.previousGrade  = prev;
        gc.newGrade       = next;
        gc.changedAt      = at;
        gc.changedBy      = "test";
        return gc;
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
