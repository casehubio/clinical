package io.casehub.clinical.cbr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.clinical.api.DsmbSafetySignalEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.service.DsmbBatchSignalNotifier;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class TrialSafetyAggregationJobTest {

    private ClinicalCbrService cbrService;
    private Event<DsmbSafetySignalEvent> signalEvent;
    private TrialSafetyAggregationJob job;
    private Clock clock;

    private UUID trialId;
    private UUID siteA, siteB, siteC, siteD, siteE;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cbrService = mock(ClinicalCbrService.class);
        signalEvent = mock(Event.class);
        clock = Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC);

        trialId = UUID.randomUUID();
        siteA = UUID.randomUUID();
        siteB = UUID.randomUUID();
        siteC = UUID.randomUUID();
        siteD = UUID.randomUUID();
        siteE = UUID.randomUUID();

        when(cbrService.storeIdempotent(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn("stored-id");

        job = new TrialSafetyAggregationJob(cbrService, clock, signalEvent,
            mock(WorkItemService.class), mock(WorkItemStore.class),
            mock(DsmbBatchSignalNotifier.class), new ObjectMapper());
        job.tenantId = "default";
        job.gradeThresholdMinGrade = 3;
        job.gradeThresholdMinSites = 3;
        job.gradeThresholdMinRate = 0.1;
        job.crossSiteClusterMinSites = 3;
        job.aggregationPeriodDays = 90;
    }

    @Test
    void detectsGradeThreshold_when3of5SitesAboveRate() {
        Map<UUID, List<TrialSafetyAggregationJob.SiteAeSummary>> siteData = Map.of(
            siteA, List.of(aeSummary(CtcaeGrade.GRADE_3, "nausea", 10), aeSummary(CtcaeGrade.GRADE_1, "fatigue", 50)),
            siteB, List.of(aeSummary(CtcaeGrade.GRADE_4, "hepatotoxicity", 3), aeSummary(CtcaeGrade.GRADE_1, "fatigue", 20)),
            siteC, List.of(aeSummary(CtcaeGrade.GRADE_3, "nausea", 8), aeSummary(CtcaeGrade.GRADE_2, "rash", 30)),
            siteD, List.of(aeSummary(CtcaeGrade.GRADE_1, "fatigue", 40)),
            siteE, List.of(aeSummary(CtcaeGrade.GRADE_1, "headache", 25))
        );

        List<TrialSafetyAggregationJob.DetectedSignal> signals = job.detectSignals(
            trialId, siteData, "PHASE_III", "default");

        assertThat(signals).anyMatch(s -> s.signalType().equals("GRADE_THRESHOLD"));
        var gradeSignal = signals.stream()
            .filter(s -> s.signalType().equals("GRADE_THRESHOLD"))
            .findFirst().orElseThrow();
        assertThat(gradeSignal.affectedSites()).hasSize(3);
        assertThat(gradeSignal.affectedSites()).containsExactlyInAnyOrder(siteA, siteB, siteC);
    }

    @Test
    void noGradeThreshold_whenBelowMinimumSites() {
        Map<UUID, List<TrialSafetyAggregationJob.SiteAeSummary>> siteData = Map.of(
            siteA, List.of(aeSummary(CtcaeGrade.GRADE_3, "nausea", 10), aeSummary(CtcaeGrade.GRADE_1, "fatigue", 50)),
            siteB, List.of(aeSummary(CtcaeGrade.GRADE_4, "hepatotoxicity", 3), aeSummary(CtcaeGrade.GRADE_1, "fatigue", 20)),
            siteC, List.of(aeSummary(CtcaeGrade.GRADE_1, "fatigue", 40)),
            siteD, List.of(aeSummary(CtcaeGrade.GRADE_1, "fatigue", 25)),
            siteE, List.of(aeSummary(CtcaeGrade.GRADE_1, "headache", 15))
        );

        List<TrialSafetyAggregationJob.DetectedSignal> signals = job.detectSignals(
            trialId, siteData, "PHASE_III", "default");

        assertThat(signals).noneMatch(s -> s.signalType().equals("GRADE_THRESHOLD"));
    }

    @Test
    void detectsCrossSiteCluster_whenSameEventTypeAt3Sites() {
        Map<UUID, List<TrialSafetyAggregationJob.SiteAeSummary>> siteData = Map.of(
            siteA, List.of(aeSummary(CtcaeGrade.GRADE_2, "hepatotoxicity", 3)),
            siteB, List.of(aeSummary(CtcaeGrade.GRADE_3, "hepatotoxicity", 2)),
            siteC, List.of(aeSummary(CtcaeGrade.GRADE_2, "hepatotoxicity", 4)),
            siteD, List.of(aeSummary(CtcaeGrade.GRADE_1, "fatigue", 20)),
            siteE, List.of(aeSummary(CtcaeGrade.GRADE_1, "headache", 10))
        );

        List<TrialSafetyAggregationJob.DetectedSignal> signals = job.detectSignals(
            trialId, siteData, "PHASE_III", "default");

        assertThat(signals).anyMatch(s -> s.signalType().equals("CROSS_SITE_CLUSTER"));
        var clusterSignal = signals.stream()
            .filter(s -> s.signalType().equals("CROSS_SITE_CLUSTER"))
            .findFirst().orElseThrow();
        assertThat(clusterSignal.affectedSites()).containsExactlyInAnyOrder(siteA, siteB, siteC);
    }

    @Test
    void noSignals_whenAllSitesBelowThresholds() {
        Map<UUID, List<TrialSafetyAggregationJob.SiteAeSummary>> siteData = Map.of(
            siteA, List.of(aeSummary(CtcaeGrade.GRADE_1, "fatigue", 40)),
            siteB, List.of(aeSummary(CtcaeGrade.GRADE_1, "headache", 30)),
            siteC, List.of(aeSummary(CtcaeGrade.GRADE_1, "nausea", 25))
        );

        List<TrialSafetyAggregationJob.DetectedSignal> signals = job.detectSignals(
            trialId, siteData, "PHASE_III", "default");

        assertThat(signals).isEmpty();
    }

    @Test
    void storesCbrCase_forDetectedSignal() {
        TrialSafetyAggregationJob.DetectedSignal signal = new TrialSafetyAggregationJob.DetectedSignal(
            "GRADE_THRESHOLD", List.of(siteA, siteB, siteC),
            "3 of 5 sites show Grade 3+ AE rate above 10%", CtcaeGrade.GRADE_3, "nausea");

        job.storeCbrCase(trialId, signal, 5, "PHASE_III", "default");

        ArgumentCaptor<PlanCbrCase> captor = ArgumentCaptor.forClass(PlanCbrCase.class);
        verify(cbrService).storeIdempotent(
            captor.capture(),
            eq("clinical-trial-safety"),
            any(String.class),
            eq(ClinicalCbrDomains.TRIAL_SAFETY),
            eq("default"),
            eq(null),
            any());

        PlanCbrCase stored = captor.getValue();
        assertThat(stored.problem()).contains("GRADE_THRESHOLD");
        assertThat(stored.problem()).contains("PHASE_III");
    }

    @Test
    void firesEvent_forNewSignal() {
        TrialSafetyAggregationJob.DetectedSignal signal = new TrialSafetyAggregationJob.DetectedSignal(
            "GRADE_THRESHOLD", List.of(siteA, siteB, siteC),
            "3 of 5 sites show Grade 3+ AE rate above 10%", CtcaeGrade.GRADE_3, "nausea");

        job.fireSignalEvent(trialId, signal, "default");

        ArgumentCaptor<DsmbSafetySignalEvent> captor = ArgumentCaptor.forClass(DsmbSafetySignalEvent.class);
        verify(signalEvent).fireAsync(captor.capture());

        DsmbSafetySignalEvent fired = captor.getValue();
        assertThat(fired.trialId()).isEqualTo(trialId);
        assertThat(fired.signalType()).isEqualTo("GRADE_THRESHOLD");
        assertThat(fired.affectedSites()).containsExactlyInAnyOrder(siteA, siteB, siteC);
    }

    private TrialSafetyAggregationJob.SiteAeSummary aeSummary(CtcaeGrade grade, String eventType, int count) {
        return new TrialSafetyAggregationJob.SiteAeSummary(grade, eventType, count);
    }
}
