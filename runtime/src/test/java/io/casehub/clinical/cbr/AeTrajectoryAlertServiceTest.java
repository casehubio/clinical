package io.casehub.clinical.cbr;

import io.casehub.clinical.api.AeTrajectoryAlertEvent;
import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.entity.AdverseEvent;
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

class AeTrajectoryAlertServiceTest {

    private AeTrajectoryBuilder trajectoryBuilder;
    private ClinicalCbrService cbrService;
    private Event<AeTrajectoryAlertEvent> alertEvents;
    private AeTrajectoryAlertService service;

    private static final UUID TEST_AE_ID = UUID.randomUUID();
    private static final UUID TEST_ENROLLMENT_ID = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        trajectoryBuilder = mock(AeTrajectoryBuilder.class);
        cbrService = mock(ClinicalCbrService.class);
        alertEvents = mock(Event.class);
        service = new AeTrajectoryAlertService(trajectoryBuilder, cbrService, alertEvents);
        service.minMatches = 2;
        service.minSimilarity = 0.5;
        service.minProbability = 0.6;
        service.setEntityFinder(AeTrajectoryAlertServiceTest::findAe);
    }

    @Test
    void aeNotFound_returnsEmpty() {
        service.setEntityFinder(id -> null);
        Optional<AeTrajectoryAlertEvent> result = service.evaluate(UUID.randomUUID(), "t1");
        assertTrue(result.isEmpty());
    }

    @Test
    void noMatches_returnsEmpty() {
        when(trajectoryBuilder.buildPartialTrajectory(any(), eq("t1"))).thenReturn(List.of(Map.of()));
        when(cbrService.retrieveWithAudit(any(), eq(PlanCbrCase.class), any(), any()))
                .thenReturn(new AuditedRetrievalResult<>(List.of(), "trace-1", null));

        Optional<AeTrajectoryAlertEvent> result = service.evaluate(TEST_AE_ID, "t1");
        assertTrue(result.isEmpty());
        verify(alertEvents, never()).fireAsync(any());
    }

    @Test
    void singleMatch_belowMinMatches_returnsEmpty() {
        when(trajectoryBuilder.buildPartialTrajectory(any(), eq("t1"))).thenReturn(List.of(Map.of()));
        var match1 = scoredCase("FAULTED", 0.9);
        when(cbrService.retrieveWithAudit(any(), eq(PlanCbrCase.class), any(), any()))
                .thenReturn(new AuditedRetrievalResult<>(List.of(match1), "trace-1", null));

        Optional<AeTrajectoryAlertEvent> result = service.evaluate(TEST_AE_ID, "t1");
        assertTrue(result.isEmpty());
    }

    @Test
    void matchesBelowProbabilityThreshold_returnsEmpty() {
        when(trajectoryBuilder.buildPartialTrajectory(any(), eq("t1"))).thenReturn(List.of(Map.of()));
        var match1 = scoredCase("COMPLETED", 0.7);
        var match2 = scoredCase("FAULTED", 0.65);
        when(cbrService.retrieveWithAudit(any(), eq(PlanCbrCase.class), any(), any()))
                .thenReturn(new AuditedRetrievalResult<>(List.of(match1, match2), "trace-1", null));

        Optional<AeTrajectoryAlertEvent> result = service.evaluate(TEST_AE_ID, "t1");
        assertTrue(result.isEmpty());
    }

    @Test
    void matchesAboveThreshold_firesEvent() {
        when(trajectoryBuilder.buildPartialTrajectory(any(), eq("t1"))).thenReturn(List.of(Map.of()));
        var match1 = scoredCase("FAULTED", 0.8);
        var match2 = scoredCase("FAULTED", 0.7);
        var match3 = scoredCase("COMPLETED", 0.5);
        when(cbrService.retrieveWithAudit(any(), eq(PlanCbrCase.class), any(), any()))
                .thenReturn(new AuditedRetrievalResult<>(List.of(match1, match2, match3), "trace-1", null));

        Optional<AeTrajectoryAlertEvent> result = service.evaluate(TEST_AE_ID, "t1");
        assertTrue(result.isPresent());
        assertEquals("FAULTED", result.get().predictedOutcome());
        assertEquals(3, result.get().matchCount());
        assertEquals(CtcaeGrade.GRADE_3, result.get().currentGrade());
        verify(alertEvents).fireAsync(any());
    }

    @Test
    void cbrRetrievalFailure_returnsEmptyGracefully() {
        when(trajectoryBuilder.buildPartialTrajectory(any(), eq("t1"))).thenReturn(List.of(Map.of()));
        when(cbrService.retrieveWithAudit(any(), eq(PlanCbrCase.class), any(), any()))
                .thenThrow(new RuntimeException("CBR store unavailable"));

        Optional<AeTrajectoryAlertEvent> result = service.evaluate(TEST_AE_ID, "t1");
        assertTrue(result.isEmpty());
        verify(alertEvents, never()).fireAsync(any());
    }

    @Test
    void weightedMajorityVoting_highSimilarityWins() {
        var cases = List.of(
            scoredCase("A", 0.9),
            scoredCase("B", 0.3),
            scoredCase("B", 0.3)
        );
        var prediction = service.predictOutcome(cases);
        assertEquals("A", prediction.outcome());
        assertTrue(prediction.probability() > 0.5);
    }

    private static AdverseEvent findAe(UUID id) {
        if (!TEST_AE_ID.equals(id)) return null;
        AdverseEvent ae = new AdverseEvent();
        ae.id = id;
        ae.grade = CtcaeGrade.GRADE_3;
        ae.enrollmentId = TEST_ENROLLMENT_ID;
        ae.engineCaseId = UUID.randomUUID();
        ae.eventType = "NAUSEA";
        ae.escalationStatus = AeEscalationStatus.REQUESTED;
        ae.susarOversightStatus = SusarOversightStatus.NONE;
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.NONE;
        ae.tenantId = "t1";
        ae.reportedAt = Instant.now();
        ae.unexpected = false;
        ae.suspected = true;
        return ae;
    }

    private ScoredCbrCase<PlanCbrCase> scoredCase(String outcome, double score) {
        var cbrCase = new PlanCbrCase("problem", "solution", outcome, 1.0, Map.of(), List.of(), null, null);
        return new ScoredCbrCase<>(cbrCase, UUID.randomUUID().toString(), score);
    }
}
