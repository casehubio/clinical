package io.casehub.clinical.cbr;

import io.casehub.clinical.api.SiteEnrollmentAlertEvent;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteEnrollmentAlertServiceTest {

    private SiteEnrollmentTrajectoryBuilder trajectoryBuilder;
    private ClinicalCbrService cbrService;
    private Event<SiteEnrollmentAlertEvent> alertEvents;
    private SiteEnrollmentAlertService service;

    private static final UUID SITE_ID = UUID.randomUUID();
    private static final UUID TRIAL_ID = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        trajectoryBuilder = mock(SiteEnrollmentTrajectoryBuilder.class);
        cbrService = mock(ClinicalCbrService.class);
        alertEvents = mock(Event.class);
        service = new SiteEnrollmentAlertService(trajectoryBuilder, cbrService, alertEvents);
        service.minMatches = 2;
        service.minSimilarity = 0.5;
        service.minProbability = 0.6;
        service.setTrialFinder(SiteEnrollmentAlertServiceTest::findTrial);
        service.setEarliestEnrollmentFinder((siteId, tenantId) -> Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void trialNotFound_returnsEmpty() {
        service.setTrialFinder(id -> null);
        Optional<SiteEnrollmentAlertEvent> result = service.evaluate(SITE_ID, TRIAL_ID, "t1");
        assertTrue(result.isEmpty());
    }

    @Test
    void noMatches_returnsEmpty() {
        when(trajectoryBuilder.buildTrajectory(eq(SITE_ID), eq(TRIAL_ID), any(), eq("t1")))
                .thenReturn(List.of(Map.of("ts", FeatureValue.number(0), "periodCount", FeatureValue.number(5), "cumulativeCount", FeatureValue.number(5))));
        when(cbrService.retrieveWithAudit(any(), eq(PlanCbrCase.class), any(), any()))
                .thenReturn(new AuditedRetrievalResult<>(List.of(), "trace-1", null));

        Optional<SiteEnrollmentAlertEvent> result = service.evaluate(SITE_ID, TRIAL_ID, "t1");
        assertTrue(result.isEmpty());
    }

    @Test
    void matchesAboveThreshold_firesEvent() {
        when(trajectoryBuilder.buildTrajectory(eq(SITE_ID), eq(TRIAL_ID), any(), eq("t1")))
                .thenReturn(List.of(Map.of("ts", FeatureValue.number(0), "periodCount", FeatureValue.number(1), "cumulativeCount", FeatureValue.number(1))));
        var match1 = scoredCase("ENROLLMENT_STALL", 0.8);
        var match2 = scoredCase("ENROLLMENT_STALL", 0.7);
        when(cbrService.retrieveWithAudit(any(), eq(PlanCbrCase.class), any(), any()))
                .thenReturn(new AuditedRetrievalResult<>(List.of(match1, match2), "trace-1", null));

        Optional<SiteEnrollmentAlertEvent> result = service.evaluate(SITE_ID, TRIAL_ID, "t1");
        assertTrue(result.isPresent());
        assertEquals("ENROLLMENT_STALL", result.get().predictedOutcome());
        assertEquals(2, result.get().matchCount());
        verify(alertEvents).fireAsync(any());
    }

    @Test
    void emptyTrajectory_returnsEmpty() {
        when(trajectoryBuilder.buildTrajectory(eq(SITE_ID), eq(TRIAL_ID), any(), eq("t1")))
                .thenReturn(List.of());

        Optional<SiteEnrollmentAlertEvent> result = service.evaluate(SITE_ID, TRIAL_ID, "t1");
        assertTrue(result.isEmpty());
    }

    @Test
    void cbrFailure_returnsEmptyGracefully() {
        when(trajectoryBuilder.buildTrajectory(eq(SITE_ID), eq(TRIAL_ID), any(), eq("t1")))
                .thenReturn(List.of(Map.of("ts", FeatureValue.number(0), "periodCount", FeatureValue.number(1), "cumulativeCount", FeatureValue.number(1))));
        when(cbrService.retrieveWithAudit(any(), eq(PlanCbrCase.class), any(), any()))
                .thenThrow(new RuntimeException("Store unavailable"));

        Optional<SiteEnrollmentAlertEvent> result = service.evaluate(SITE_ID, TRIAL_ID, "t1");
        assertTrue(result.isEmpty());
    }

    private static ClinicalTrial findTrial(UUID id) {
        if (!TRIAL_ID.equals(id)) return null;
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = id;
        trial.phase = TrialPhase.PHASE_III;
        trial.tenantId = "t1";
        return trial;
    }

    private ScoredCbrCase<PlanCbrCase> scoredCase(String outcome, double score) {
        var cbrCase = new PlanCbrCase("problem", "solution", outcome, 1.0, Map.of(), List.of());
        return new ScoredCbrCase<>(cbrCase, UUID.randomUUID().toString(), score);
    }
}
