package io.casehub.clinical.cbr;

import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.engine.common.spi.PlanItemStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;

class AeCbrCaseBuilderTest {

    private ClinicalCbrService cbrService;
    private ClinicalScopeResolver scopeResolver;
    private PlanItemStore planItemStore;
    private AeTrajectoryBuilder trajectoryBuilder;
    private io.casehub.ledger.runtime.repository.ActorTrustScoreRepository trustScoreRepository;
    private AeCbrCaseBuilder builder;

    private AdverseEvent ae;
    private PatientEnrollment enrollment;
    private TrialSite site;
    private ClinicalTrial trial;

    @BeforeEach
    void setUp() {
        cbrService = mock(ClinicalCbrService.class);
        scopeResolver = mock(ClinicalScopeResolver.class);
        planItemStore = mock(PlanItemStore.class);
        trajectoryBuilder = mock(AeTrajectoryBuilder.class);
        trustScoreRepository = mock(io.casehub.ledger.runtime.repository.ActorTrustScoreRepository.class);

        builder = spy(new AeCbrCaseBuilder(cbrService, scopeResolver, planItemStore,
            trajectoryBuilder, trustScoreRepository));
        doReturn(0L).when(builder).countPriorAes(any(), any());
        doReturn(0L).when(builder).countEnrollmentsAtSite(any());

        ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.grade = CtcaeGrade.GRADE_3;
        ae.eventType = "NAUSEA";
        ae.unexpected = true;
        ae.suspected = true;
        ae.enrollmentId = UUID.randomUUID();
        ae.engineCaseId = UUID.randomUUID();
        ae.tenantId = "default";
        ae.escalationStatus = AeEscalationStatus.COMPLETED;

        enrollment = new PatientEnrollment();
        enrollment.id = ae.enrollmentId;
        enrollment.siteId = UUID.randomUUID();
        enrollment.treatmentArm = "ARM_A";

        site = new TrialSite();
        site.id = enrollment.siteId;
        site.trialId = UUID.randomUUID();
        site.targetEnrollment = 100;

        trial = new ClinicalTrial();
        trial.id = site.trialId;
        trial.phase = TrialPhase.PHASE_III;

        when(scopeResolver.forAdverseEvent(ae)).thenReturn(
            Optional.of(Path.of(trial.id.toString(), site.id.toString(), "P-001")));
        when(planItemStore.findByCaseId(any(), any())).thenReturn(List.of());
        when(trajectoryBuilder.buildTrajectory(any(), any())).thenReturn(List.of());
    }

    @Test
    void buildAndStore_storesAeCbrCaseWithCorrectFeatures() {
        builder.buildAndStore(ae, enrollment, site, trial,
            "RESOLVED", true, null, ae.engineCaseId, ae.tenantId);

        verify(cbrService).storeIdempotent(any(PlanCbrCase.class), eq("clinical-ae"),
            eq(ae.id.toString()), eq(ClinicalCbrDomains.AE), eq("default"),
            eq(ae.engineCaseId.toString()), any(Path.class));
    }

    @Test
    void buildAndStore_withRegradeSource_setsFeature() {
        ArgumentCaptor<PlanCbrCase> captor = ArgumentCaptor.forClass(PlanCbrCase.class);

        builder.buildAndStore(ae, enrollment, site, trial,
            null, false, "regrade", ae.engineCaseId, ae.tenantId);

        verify(cbrService).storeIdempotent(captor.capture(), eq("clinical-ae"),
            eq(ae.id.toString()), any(), any(), any(), any());
        PlanCbrCase stored = captor.getValue();
        assertEquals(FeatureValue.string("regrade"),
            stored.features().get("regradeSource"));
    }

    @Test
    void buildAndStore_withoutRegradeSource_noRegradeSourceFeature() {
        ArgumentCaptor<PlanCbrCase> captor = ArgumentCaptor.forClass(PlanCbrCase.class);

        builder.buildAndStore(ae, enrollment, site, trial,
            "RESOLVED", true, null, ae.engineCaseId, ae.tenantId);

        verify(cbrService).storeIdempotent(captor.capture(), eq("clinical-ae"),
            eq(ae.id.toString()), any(), any(), any(), any());
        PlanCbrCase stored = captor.getValue();
        assertNull(stored.features().get("regradeSource"));
    }

    @Test
    void buildAndStore_scopeResolutionFails_skips() {
        when(scopeResolver.forAdverseEvent(ae)).thenReturn(Optional.empty());

        builder.buildAndStore(ae, enrollment, site, trial,
            null, false, null, ae.engineCaseId, ae.tenantId);

        verify(cbrService, never()).storeIdempotent(
            any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void buildAndStore_storesTrajectoryCase() {
        when(trajectoryBuilder.buildTrajectory(ae, "default"))
            .thenReturn(List.of(new java.util.LinkedHashMap<>()));

        builder.buildAndStore(ae, enrollment, site, trial,
            null, false, null, ae.engineCaseId, ae.tenantId);

        verify(cbrService, times(2)).storeIdempotent(
            any(), any(), any(), any(), any(), any(), any());
        verify(cbrService).storeIdempotent(any(), eq("clinical-ae-trajectory"),
            eq(ae.id + "-trajectory"), eq(ClinicalCbrDomains.AE_TRAJECTORY),
            any(), any(), any());
    }
}
