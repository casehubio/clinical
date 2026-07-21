package io.casehub.clinical.service;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.AeGradeChange;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.memory.ClinicalMemoryService;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.casehub.clinical.api.ClinicalGroups.COORDINATOR;
import static io.casehub.clinical.api.ClinicalGroups.INVESTIGATOR;
import static io.casehub.clinical.api.ClinicalGroups.SPONSOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {SPONSOR, INVESTIGATOR, COORDINATOR})
class AdverseEventRegradeTest {

    @Inject AdverseEventService service;
    @Inject FixedCurrentPrincipal principal;
    @InjectMock AeGradeChangeLedgerWriter gradeChangeLedgerWriter;
    @InjectMock ClinicalMemoryService memoryService;

    private UUID aeId;

    @BeforeEach
    @Transactional
    void setup() {
        AeGradeChange.deleteAll();
        AdverseEvent.deleteAll();
        PatientEnrollment.deleteAll();
        TrialSite.deleteAll();

        UUID trialId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();

        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = "inv-1";
        site.tenantId = principal.tenancyId();
        site.persist();

        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = enrollmentId;
        enrollment.siteId = siteId;
        enrollment.patientId = "P-001";
        enrollment.tenantId = principal.tenancyId();
        enrollment.persist();

        aeId = UUID.randomUUID();
        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.enrollmentId = enrollmentId;
        ae.grade = CtcaeGrade.GRADE_1;
        ae.occurredAt = Instant.now().minus(Duration.ofHours(2));
        ae.reportedAt = Instant.now().minus(Duration.ofHours(1));
        ae.slaDeadline = ae.reportedAt.plus(Duration.ofDays(7));
        ae.tenantId = principal.tenancyId();
        ae.persist();
    }

    @Test
    void regrade_updatesGradeAndCreatesHistory() {
        service.regradeAdverseEvent(aeId, CtcaeGrade.GRADE_3, "dr-smith", "Condition worsened");

        AdverseEvent ae = AdverseEvent.findById(aeId);
        assertEquals(CtcaeGrade.GRADE_3, ae.grade);

        List<AeGradeChange> history = AeGradeChange.findByAdverseEventId(aeId);
        assertEquals(1, history.size());
        assertEquals(CtcaeGrade.GRADE_1, history.get(0).previousGrade);
        assertEquals(CtcaeGrade.GRADE_3, history.get(0).newGrade);
        assertEquals("dr-smith", history.get(0).changedBy);
        assertEquals("Condition worsened", history.get(0).reason);
    }

    @Test
    void regrade_sameGrade_noOp() {
        service.regradeAdverseEvent(aeId, CtcaeGrade.GRADE_1, "dr-smith", "No change");

        List<AeGradeChange> history = AeGradeChange.findByAdverseEventId(aeId);
        assertTrue(history.isEmpty());
        verify(gradeChangeLedgerWriter, never()).writeGradeChangeEntry(any(), any(), any());
    }

    @Test
    @Transactional
    void regrade_upgrade_tightensSla() {
        AdverseEvent aeBefore    = AdverseEvent.findById(aeId);
        Instant      oldDeadline = aeBefore.slaDeadline;

        service.regradeAdverseEvent(aeId, CtcaeGrade.GRADE_3, "dr-smith", "Escalated");

        AdverseEvent ae = AdverseEvent.findById(aeId);
        assertTrue(ae.slaDeadline.isBefore(oldDeadline),
                   "SLA should tighten on upgrade: new=" + ae.slaDeadline + " old=" + oldDeadline);
        long hoursUntilDeadline = Duration.between(Instant.now(), ae.slaDeadline).toHours();
        assertTrue(hoursUntilDeadline <= 24, "SLA should be ~24h for Grade 3, was " + hoursUntilDeadline + "h");
    }

    @Test
    @Transactional
    void regrade_downgrade_doesNotRelaxSla() {
        service.regradeAdverseEvent(aeId, CtcaeGrade.GRADE_3, "dr-smith", "Up");
        AdverseEvent aeAfterUpgrade = AdverseEvent.findById(aeId);
        Instant      tightDeadline  = aeAfterUpgrade.slaDeadline;

        service.regradeAdverseEvent(aeId, CtcaeGrade.GRADE_1, "dr-smith", "Down");

        AdverseEvent ae = AdverseEvent.findById(aeId);
        assertEquals(tightDeadline, ae.slaDeadline, "Downgrade should not relax SLA");
    }

    @Test
    void regrade_writesLedgerEntry() {
        service.regradeAdverseEvent(aeId, CtcaeGrade.GRADE_3, "dr-smith", "Worsened");

        verify(gradeChangeLedgerWriter).writeGradeChangeEntry(any(), eq(CtcaeGrade.GRADE_1), eq("Worsened"));
    }

    @Test
    void regrade_storesMemory() {
        service.regradeAdverseEvent(aeId, CtcaeGrade.GRADE_3, "dr-smith", "Worsened");

        verify(memoryService).storeAeRegrade(eq(aeId), any(), any(), any(),
            eq(CtcaeGrade.GRADE_1), eq(CtcaeGrade.GRADE_3), eq(principal.tenancyId()));
    }

    @Test
    void regrade_nonexistentAe_noOp() {
        service.regradeAdverseEvent(UUID.randomUUID(), CtcaeGrade.GRADE_3, "dr-smith", "test");
        verify(gradeChangeLedgerWriter, never()).writeGradeChangeEntry(any(), any(), any());
    }
}
