package io.casehub.clinical.service;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.spi.AdverseEventContext;
import io.casehub.clinical.api.spi.AdverseEventEscalationPolicy;
import io.casehub.clinical.api.spi.AdverseEventEscalationRequirements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AeEscalationCaseServiceTest {

    @Mock ClinicalAdverseEventCaseHub caseHub;
    @Mock AdverseEventEscalationPolicy policy;
    @Mock CaseHubRuntime runtime;
    @Mock TrialCaseLookup trialCaseLookup;
    @InjectMocks AeEscalationCaseService service;

    @Test
    void grade3_starts_case_with_senior_monitor_context() {
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        var event = new AdverseEventReportedEvent(aeId, enrollmentId, siteId, CtcaeGrade.GRADE_3, Instant.now());

        when(policy.evaluate(any())).thenReturn(
                AdverseEventEscalationRequirements.engineManaged(true, false));
        when(caseHub.startCase(any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        service.onAdverseEventReported(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(caseHub).startCase(captor.capture());
        Map<String, Object> ctx = captor.getValue();

        assertThat(ctx.get("aeId")).isEqualTo(aeId.toString());
        assertThat(ctx.get("grade")).isEqualTo("GRADE_3");
        assertThat(ctx.get("requiresSeniorMonitor")).isEqualTo(true);
        assertThat(ctx.get("requiresDsmbEscalation")).isEqualTo(false);
    }

    @Test
    void siteId_included_in_case_context() {
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        var event = new AdverseEventReportedEvent(aeId, enrollmentId, siteId, CtcaeGrade.GRADE_3, Instant.now());

        when(policy.evaluate(any())).thenReturn(
                AdverseEventEscalationRequirements.engineManaged(true, false));
        when(caseHub.startCase(any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        service.onAdverseEventReported(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(caseHub).startCase(captor.capture());
        assertThat(captor.getValue().get("siteId")).isEqualTo(siteId.toString());
    }

    @Test
    void grade4_starts_case_with_dsmb_context() {
        var event = new AdverseEventReportedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CtcaeGrade.GRADE_4, Instant.now());

        when(policy.evaluate(any())).thenReturn(
                AdverseEventEscalationRequirements.engineManaged(true, true));
        when(caseHub.startCase(any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        service.onAdverseEventReported(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(caseHub).startCase(captor.capture());
        assertThat(captor.getValue().get("requiresDsmbEscalation")).isEqualTo(true);
    }

    @Test
    void grade4_signals_trial_case_grade4_active() {
        UUID siteId = UUID.randomUUID();
        UUID trialCaseId = UUID.randomUUID();
        var event = new AdverseEventReportedEvent(
                UUID.randomUUID(), UUID.randomUUID(), siteId, CtcaeGrade.GRADE_4, Instant.now());

        when(policy.evaluate(any())).thenReturn(
                AdverseEventEscalationRequirements.engineManaged(true, true));
        when(caseHub.startCase(any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
        when(trialCaseLookup.findTrialEngineCase(siteId)).thenReturn(trialCaseId);

        service.onAdverseEventReported(event);

        verify(runtime).signal(trialCaseId, "grade4Active." + siteId, Boolean.TRUE);
    }

    @Test
    void grade5_signals_trial_case_grade4_active() {
        UUID siteId = UUID.randomUUID();
        UUID trialCaseId = UUID.randomUUID();
        var event = new AdverseEventReportedEvent(
                UUID.randomUUID(), UUID.randomUUID(), siteId, CtcaeGrade.GRADE_5, Instant.now());

        when(policy.evaluate(any())).thenReturn(
                AdverseEventEscalationRequirements.engineManaged(true, true));
        when(caseHub.startCase(any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
        when(trialCaseLookup.findTrialEngineCase(siteId)).thenReturn(trialCaseId);

        service.onAdverseEventReported(event);

        verify(runtime).signal(trialCaseId, "grade4Active." + siteId, Boolean.TRUE);
    }

    @Test
    void grade3_does_not_signal_trial_case() {
        var event = new AdverseEventReportedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CtcaeGrade.GRADE_3, Instant.now());

        when(policy.evaluate(any())).thenReturn(
                AdverseEventEscalationRequirements.engineManaged(true, false));
        when(caseHub.startCase(any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        service.onAdverseEventReported(event);

        verifyNoInteractions(runtime);
        verifyNoInteractions(trialCaseLookup);
    }

    @Test
    void grade4_no_trial_case_signal_skipped_gracefully() {
        UUID siteId = UUID.randomUUID();
        var event = new AdverseEventReportedEvent(
                UUID.randomUUID(), UUID.randomUUID(), siteId, CtcaeGrade.GRADE_4, Instant.now());

        when(policy.evaluate(any())).thenReturn(
                AdverseEventEscalationRequirements.engineManaged(true, true));
        when(caseHub.startCase(any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
        when(trialCaseLookup.findTrialEngineCase(siteId)).thenReturn(null); // trial not yet active

        service.onAdverseEventReported(event);

        verifyNoInteractions(runtime); // signal not called when trial has no engine case
    }
}
