package io.casehub.clinical.api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClinicalScopeTest {

    @Test
    void trialHasDepthOne() {
        assertEquals(1, ClinicalScope.TRIAL.depth());
    }

    @Test
    void siteHasDepthTwo() {
        assertEquals(2, ClinicalScope.SITE.depth());
    }

    @Test
    void patientHasDepthThree() {
        assertEquals(3, ClinicalScope.PATIENT.depth());
    }
}
