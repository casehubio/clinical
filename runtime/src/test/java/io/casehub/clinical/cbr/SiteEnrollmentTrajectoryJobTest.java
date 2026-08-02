package io.casehub.clinical.cbr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class SiteEnrollmentTrajectoryJobTest {

    private SiteEnrollmentTrajectoryBuilder trajectoryBuilder;
    private ClinicalCbrService cbrService;
    private SiteEnrollmentTrajectoryJob job;

    @BeforeEach
    void setUp() {
        trajectoryBuilder = new SiteEnrollmentTrajectoryBuilder();
        cbrService = Mockito.mock(ClinicalCbrService.class);
        job = new SiteEnrollmentTrajectoryJob(trajectoryBuilder, cbrService, new ClinicalScopeResolver());
    }

    @Test
    void snapshotSite_stores_trajectory_cbr_case() {
        UUID siteId = UUID.randomUUID();
        UUID trialId = UUID.randomUUID();
        Instant trialStart = Instant.now().minusSeconds(86400 * 21);
        String tenantId = "default";

        // Set up trajectory builder with test data
        trajectoryBuilder.setEnrollmentQuery((sid, tid) ->
            List.of(
                trialStart.plusSeconds(86400),       // week 0
                trialStart.plusSeconds(86400 * 3),   // week 0
                trialStart.plusSeconds(86400 * 8),   // week 1
                trialStart.plusSeconds(86400 * 15),  // week 2
                trialStart.plusSeconds(86400 * 16)   // week 2
            ));

        when(cbrService.storeIdempotent(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn("stored-id");

        job.snapshotSite(siteId, trialId, trialStart, 100, "PHASE_III", tenantId);

        ArgumentCaptor<PlanCbrCase> caseCaptor = ArgumentCaptor.forClass(PlanCbrCase.class);
        verify(cbrService).storeIdempotent(
            caseCaptor.capture(),
            eq("clinical-site-enrollment"),
            any(String.class),
            eq(ClinicalCbrDomains.SITE_ENROLLMENT),
            eq(tenantId),
            eq(null),
            any());

        PlanCbrCase stored = caseCaptor.getValue();
        assertThat(stored.problem()).contains("PHASE_III");
        assertThat(stored.outcome()).isEqualTo("IN_PROGRESS");

        FeatureValue trajFeature = stored.features().get("enrollmentTrajectory");
        assertThat(trajFeature).isInstanceOf(FeatureValue.StructListVal.class);
        var observations = ((FeatureValue.StructListVal) trajFeature).items();
        assertThat(observations).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void snapshotSite_skips_when_no_trajectory() {
        UUID siteId = UUID.randomUUID();
        UUID trialId = UUID.randomUUID();
        String tenantId = "default";

        trajectoryBuilder.setEnrollmentQuery((sid, tid) -> List.of());

        job.snapshotSite(siteId, trialId, Instant.now(), 100, "PHASE_III", tenantId);

        verify(cbrService, never()).storeIdempotent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void snapshotSite_includes_enrollment_progress() {
        UUID siteId = UUID.randomUUID();
        UUID trialId = UUID.randomUUID();
        Instant trialStart = Instant.now().minusSeconds(86400 * 14);
        String tenantId = "default";

        trajectoryBuilder.setEnrollmentQuery((sid, tid) ->
            List.of(trialStart.plusSeconds(86400), trialStart.plusSeconds(86400 * 8)));

        when(cbrService.storeIdempotent(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn("stored-id");

        job.snapshotSite(siteId, trialId, trialStart, 50, "PHASE_II", tenantId);

        ArgumentCaptor<PlanCbrCase> captor = ArgumentCaptor.forClass(PlanCbrCase.class);
        verify(cbrService).storeIdempotent(captor.capture(), any(), any(), any(), any(), any(), any());

        PlanCbrCase stored = captor.getValue();
        FeatureValue progress = stored.features().get("enrollmentProgress");
        assertThat(progress).isInstanceOf(FeatureValue.NumberVal.class);
        assertThat(((FeatureValue.NumberVal) progress).value()).isCloseTo(0.04, org.assertj.core.data.Offset.offset(0.01));
    }
}
