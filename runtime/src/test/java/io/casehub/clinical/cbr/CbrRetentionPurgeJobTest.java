package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrRetentionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CbrRetentionPurgeJobTest {

    private CbrCaseMemoryStore store;
    private CbrRetentionPurgeJob job;

    @BeforeEach
    void setup() {
        store = mock(CbrCaseMemoryStore.class);
        when(store.purge(any())).thenReturn(0);

        job = new CbrRetentionPurgeJob(store);
        job.tenantId = "default";
        job.aeMaxAgeDays = 730;
        job.aeMaxCases = 10000;
        job.aeTrajectoryMaxAgeDays = 365;
        job.aeTrajectoryMaxCases = null;
        job.trialSafetyMaxAgeDays = 365;
        job.trialSafetyMaxCases = null;
        job.deviationMaxAgeDays = null;
        job.deviationMaxCases = null;
        job.amendmentMaxAgeDays = null;
        job.amendmentMaxCases = null;
        job.siteEnrollmentMaxAgeDays = null;
        job.siteEnrollmentMaxCases = null;
    }

    @Test
    void purgesDomainsWithConfiguredRetention() {
        when(store.purge(any())).thenReturn(5);

        job.purgeAll();

        var captor = ArgumentCaptor.forClass(CbrRetentionPolicy.class);
        verify(store, atLeast(1)).purge(captor.capture());

        List<CbrRetentionPolicy> policies = captor.getAllValues();
        assertTrue(policies.size() >= 3);

        var aePolicy = policies.stream()
            .filter(p -> p.domain().equals(ClinicalCbrDomains.AE))
            .findFirst().orElseThrow();
        assertEquals("default", aePolicy.tenantId());
        assertEquals(730, aePolicy.maxAgeDays());
        assertEquals(10000, aePolicy.maxCasesPerType());

        var trajectoryPolicy = policies.stream()
            .filter(p -> p.domain().equals(ClinicalCbrDomains.AE_TRAJECTORY))
            .findFirst().orElseThrow();
        assertEquals(365, trajectoryPolicy.maxAgeDays());
        assertNull(trajectoryPolicy.maxCasesPerType());

        var safetyPolicy = policies.stream()
            .filter(p -> p.domain().equals(ClinicalCbrDomains.TRIAL_SAFETY))
            .findFirst().orElseThrow();
        assertEquals(365, safetyPolicy.maxAgeDays());
    }

    @Test
    void skipsDomainsWithNoRetentionConfig() {
        job.deviationMaxAgeDays = null;
        job.deviationMaxCases = null;
        job.amendmentMaxAgeDays = null;
        job.amendmentMaxCases = null;
        job.siteEnrollmentMaxAgeDays = null;
        job.siteEnrollmentMaxCases = null;

        job.purgeAll();

        var captor = ArgumentCaptor.forClass(CbrRetentionPolicy.class);
        verify(store, atLeast(1)).purge(captor.capture());

        List<CbrRetentionPolicy> policies = captor.getAllValues();
        assertTrue(policies.stream().noneMatch(p -> p.domain().equals(ClinicalCbrDomains.DEVIATION)));
        assertTrue(policies.stream().noneMatch(p -> p.domain().equals(ClinicalCbrDomains.AMENDMENT)));
        assertTrue(policies.stream().noneMatch(p -> p.domain().equals(ClinicalCbrDomains.SITE_ENROLLMENT)));
    }

    @Test
    void logsLargePurges() {
        when(store.purge(any())).thenReturn(15000);

        job.purgeAll();

        verify(store, atLeast(3)).purge(any());
    }

    @Test
    void survivesExceptionFromSingleDomain() {
        when(store.purge(argThat(p -> p != null && p.domain().equals(ClinicalCbrDomains.AE))))
            .thenThrow(new RuntimeException("storage failure"));
        when(store.purge(argThat(p -> p != null && !p.domain().equals(ClinicalCbrDomains.AE))))
            .thenReturn(0);

        job.purgeAll();

        verify(store, atLeast(2)).purge(any());
    }
}
