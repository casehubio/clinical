package io.casehub.clinical.cbr;

import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClinicalScopeResolverTest {

    private ClinicalScopeResolver resolver;
    private UUID trialId, siteId, enrollmentId;
    private String patientId;

    @BeforeEach
    void setup() {
        trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();
        patientId = UUID.randomUUID().toString();

        resolver = new ClinicalScopeResolver();
        resolver.setEntityResolver(new ClinicalScopeResolver.EntityResolver() {
            @Override
            public PatientEnrollment findEnrollment(UUID id) {
                if (!id.equals(enrollmentId)) return null;
                PatientEnrollment e = new PatientEnrollment();
                e.id = enrollmentId;
                e.siteId = siteId;
                e.patientId = patientId;
                return e;
            }

            @Override
            public TrialSite findSite(UUID id) {
                if (!id.equals(siteId)) return null;
                TrialSite s = new TrialSite();
                s.id = siteId;
                s.trialId = trialId;
                return s;
            }

            @Override
            public ClinicalTrial findTrial(UUID id) {
                if (!id.equals(trialId)) return null;
                ClinicalTrial t = new ClinicalTrial();
                t.id = trialId;
                return t;
            }
        });
    }

    @Test
    void forAdverseEvent_returnsPatientScope() {
        AdverseEvent ae = new AdverseEvent();
        ae.enrollmentId = enrollmentId;

        Optional<Path> scope = resolver.forAdverseEvent(ae);

        assertTrue(scope.isPresent());
        assertEquals(Path.of(trialId.toString(), siteId.toString(), patientId), scope.get());
        assertEquals(3, scope.get().depth());
    }

    @Test
    void forAdverseEvent_returnsEmptyWhenEnrollmentNotFound() {
        AdverseEvent ae = new AdverseEvent();
        ae.enrollmentId = UUID.randomUUID();

        assertTrue(resolver.forAdverseEvent(ae).isEmpty());
    }

    @Test
    void forAdverseEvent_returnsEmptyWhenEnrollmentIdNull() {
        AdverseEvent ae = new AdverseEvent();
        ae.enrollmentId = null;

        assertTrue(resolver.forAdverseEvent(ae).isEmpty());
    }

    @Test
    void forAdverseEvent_returnsEmptyWhenSiteNotFound() {
        UUID orphanEnrollmentId = UUID.randomUUID();
        UUID unknownSiteId = UUID.randomUUID();

        resolver.setEntityResolver(new ClinicalScopeResolver.EntityResolver() {
            @Override
            public PatientEnrollment findEnrollment(UUID id) {
                if (!id.equals(orphanEnrollmentId)) return null;
                PatientEnrollment e = new PatientEnrollment();
                e.id = orphanEnrollmentId;
                e.siteId = unknownSiteId;
                e.patientId = "p1";
                return e;
            }

            @Override
            public TrialSite findSite(UUID id) { return null; }

            @Override
            public ClinicalTrial findTrial(UUID id) { return null; }
        });

        AdverseEvent ae = new AdverseEvent();
        ae.enrollmentId = orphanEnrollmentId;

        assertTrue(resolver.forAdverseEvent(ae).isEmpty());
    }

    @Test
    void forDeviation_returnsSiteScope() {
        ProtocolDeviation dev = new ProtocolDeviation();
        dev.siteId = siteId;

        Optional<Path> scope = resolver.forDeviation(dev);

        assertTrue(scope.isPresent());
        assertEquals(Path.of(trialId.toString(), siteId.toString()), scope.get());
        assertEquals(2, scope.get().depth());
    }

    @Test
    void forDeviation_returnsEmptyWhenSiteNotFound() {
        ProtocolDeviation dev = new ProtocolDeviation();
        dev.siteId = UUID.randomUUID();

        assertTrue(resolver.forDeviation(dev).isEmpty());
    }

    @Test
    void forAmendment_returnsTrialScope() {
        ProtocolAmendment amend = new ProtocolAmendment();
        amend.trialId = trialId;

        Optional<Path> scope = resolver.forAmendment(amend);

        assertTrue(scope.isPresent());
        assertEquals(Path.of(trialId.toString()), scope.get());
        assertEquals(1, scope.get().depth());
    }

    @Test
    void forAmendment_returnsEmptyWhenTrialIdNull() {
        ProtocolAmendment amend = new ProtocolAmendment();
        amend.trialId = null;

        assertTrue(resolver.forAmendment(amend).isEmpty());
    }

    @Test
    void forSiteEnrollment_returnsSiteScope() {
        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;

        Optional<Path> scope = resolver.forSiteEnrollment(site);

        assertTrue(scope.isPresent());
        assertEquals(Path.of(trialId.toString(), siteId.toString()), scope.get());
    }

    @Test
    void forSiteEnrollment_returnsEmptyWhenTrialIdNull() {
        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = null;

        assertTrue(resolver.forSiteEnrollment(site).isEmpty());
    }

    @Test
    void forTrial_returnsTrialScope() {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;

        Optional<Path> scope = resolver.forTrial(trial);

        assertTrue(scope.isPresent());
        assertEquals(Path.of(trialId.toString()), scope.get());
    }

    @Test
    void forTrial_returnsEmptyWhenIdNull() {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = null;

        assertTrue(resolver.forTrial(trial).isEmpty());
    }

    @Test
    void scopeHierarchy_trialIsAncestorOfSite() {
        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;

        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;

        Path trialScope = resolver.forTrial(trial).orElseThrow();
        Path siteScope = resolver.forSiteEnrollment(site).orElseThrow();

        assertTrue(trialScope.isAncestorOf(siteScope));
        assertFalse(siteScope.isAncestorOf(trialScope));
    }

    @Test
    void scopeHierarchy_siteIsAncestorOfPatient() {
        AdverseEvent ae = new AdverseEvent();
        ae.enrollmentId = enrollmentId;

        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;

        Path patientScope = resolver.forAdverseEvent(ae).orElseThrow();
        Path siteScope = resolver.forSiteEnrollment(site).orElseThrow();

        assertTrue(siteScope.isAncestorOf(patientScope));
        assertFalse(patientScope.isAncestorOf(siteScope));
    }
}
