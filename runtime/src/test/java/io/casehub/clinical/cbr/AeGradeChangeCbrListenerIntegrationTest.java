package io.casehub.clinical.cbr;

import io.casehub.clinical.api.AeGradeChangedEvent;
import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.AeGradeChange;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.casehub.clinical.api.ClinicalGroups.COORDINATOR;
import static io.casehub.clinical.api.ClinicalGroups.INVESTIGATOR;
import static io.casehub.clinical.api.ClinicalGroups.SPONSOR;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {SPONSOR, INVESTIGATOR, COORDINATOR})
class AeGradeChangeCbrListenerIntegrationTest {

    @Inject AeGradeChangeCbrListener listener;
    @Inject AeCbrCaseBuilder caseBuilder;
    @Inject ClinicalCbrService cbrService;
    @Inject FixedCurrentPrincipal principal;
    @Inject
            jakarta.persistence.EntityManager em;


    private UUID aeId;
    private UUID trialId;
    private UUID siteId;
    private UUID enrollmentId;

    @BeforeEach
    @Transactional
    void setup() {
        em.createQuery("DELETE FROM AeGradeChange").executeUpdate();
        em.createQuery("DELETE FROM AdverseEvent").executeUpdate();
        em.createQuery("DELETE FROM PatientEnrollment").executeUpdate();
        em.createQuery("DELETE FROM TrialSite").executeUpdate();
        em.createQuery("DELETE FROM ClinicalTrial").executeUpdate();

        trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();
        aeId = UUID.randomUUID();

        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.protocolId = "PROTO-001";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "TestSponsor";
        trial.tenantId = principal.tenancyId();
        em.persist(trial);

        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = "inv-1";
        site.targetEnrollment = 50;
        site.tenantId = principal.tenancyId();
        em.persist(site);

        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = enrollmentId;
        enrollment.siteId = siteId;
        enrollment.patientId = "P-001";
        enrollment.tenantId = principal.tenancyId();
        em.persist(enrollment);

        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.enrollmentId = enrollmentId;
        ae.grade = CtcaeGrade.GRADE_4;
        ae.eventType = "HEPATOTOXICITY";
        ae.unexpected = true;
        ae.suspected = true;
        ae.occurredAt = Instant.now().minus(Duration.ofHours(4));
        ae.reportedAt = Instant.now().minus(Duration.ofHours(3));
        ae.slaDeadline = ae.reportedAt.plus(Duration.ofHours(24));
        ae.escalationStatus = AeEscalationStatus.COMPLETED;
        ae.engineCaseId = UUID.randomUUID();
        ae.tenantId = principal.tenancyId();
        em.persist(ae);
    }

    @Test
    @Transactional
    void regrade_restoresCbrCaseWithRegradeSource() {
        var event = new AeGradeChangedEvent(aeId, enrollmentId, siteId,
            CtcaeGrade.GRADE_3, CtcaeGrade.GRADE_4, Instant.now(),
            "dr-smith", principal.tenancyId());

        AdverseEvent ae = em.find(AdverseEvent.class, aeId);
        PatientEnrollment enrollment = em.find(PatientEnrollment.class, enrollmentId);
        TrialSite site = em.find(TrialSite.class, siteId);
        ClinicalTrial trial = em.find(ClinicalTrial.class, site.trialId);

        caseBuilder.buildAndStore(ae, enrollment, site, trial,
            null, false, "regrade", ae.engineCaseId, principal.tenancyId());

        var query = CbrQuery.of(principal.tenancyId(), ClinicalCbrDomains.AE,
            Path.of(trialId.toString(), siteId.toString(), "P-001"),
            "clinical-ae", Map.of(), 100);
        List<CbrMatch<CbrFeatureRecord>> cases = cbrService.retrieveSimilar(query, CbrFeatureRecord.class);
        assertFalse(cases.isEmpty(), "CBR case should be stored");

        CbrFeatureRecord stored = cases.get(0).cbrRecord();
        assertNotNull(stored.features().get("regradeSource"),
            "regradeSource should be set");
    }

    @Test
    void regrade_noneStatus_doesNotStoreCbrCase() {
        setEscalationStatus(AeEscalationStatus.NONE);

        var event = new AeGradeChangedEvent(aeId, enrollmentId, siteId,
            CtcaeGrade.GRADE_1, CtcaeGrade.GRADE_2, Instant.now(),
            "dr-smith", principal.tenancyId());

        listener.onGradeChanged(event);

        var query = CbrQuery.of(principal.tenancyId(), ClinicalCbrDomains.AE,
            Path.of(trialId.toString(), siteId.toString(), "P-001"),
            "clinical-ae", Map.of(), 100);
        List<CbrMatch<CbrFeatureRecord>> cases = cbrService.retrieveSimilar(query, CbrFeatureRecord.class);
        assertTrue(cases.isEmpty(), "No CBR case should be stored for non-COMPLETED AE");
    }

    @Transactional
    void setEscalationStatus(AeEscalationStatus status) {
        AdverseEvent ae = em.find(AdverseEvent.class, aeId);
        ae.escalationStatus = status;
        ae.engineCaseId = null;
    }
}
