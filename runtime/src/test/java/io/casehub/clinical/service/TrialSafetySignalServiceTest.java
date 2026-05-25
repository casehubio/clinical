package io.casehub.clinical.service;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.clinical.api.AeEscalationCompletedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrialSafetySignalServiceTest {

    @Mock CaseHubRuntime runtime;
    @Mock TrialCaseLookup trialCaseLookup;
    @InjectMocks TrialSafetySignalService service;

    @Test
    void grade4_completion_clears_trial_grade4_flag() {
        UUID siteId = UUID.randomUUID();
        UUID trialCaseId = UUID.randomUUID();
        when(trialCaseLookup.findTrialEngineCase(siteId)).thenReturn(trialCaseId);

        service.onAeEscalationCompleted(completedEvent(siteId, CtcaeGrade.GRADE_4));

        verify(runtime).signal(trialCaseId, "grade4Active." + siteId, Boolean.FALSE);
    }

    @Test
    void grade5_completion_clears_trial_grade4_flag() {
        UUID siteId = UUID.randomUUID();
        UUID trialCaseId = UUID.randomUUID();
        when(trialCaseLookup.findTrialEngineCase(siteId)).thenReturn(trialCaseId);

        service.onAeEscalationCompleted(completedEvent(siteId, CtcaeGrade.GRADE_5));

        verify(runtime).signal(trialCaseId, "grade4Active." + siteId, Boolean.FALSE);
    }

    @Test
    void grade3_completion_does_not_touch_trial() {
        service.onAeEscalationCompleted(completedEvent(UUID.randomUUID(), CtcaeGrade.GRADE_3));

        verifyNoInteractions(runtime);
        verifyNoInteractions(trialCaseLookup);
    }

    @Test
    void grade4_completion_when_trial_not_active_skipped_gracefully() {
        UUID siteId = UUID.randomUUID();
        when(trialCaseLookup.findTrialEngineCase(siteId)).thenReturn(null);

        service.onAeEscalationCompleted(completedEvent(siteId, CtcaeGrade.GRADE_4));

        verifyNoInteractions(runtime);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private AeEscalationCompletedEvent completedEvent(UUID siteId, CtcaeGrade grade) {
        return new AeEscalationCompletedEvent(UUID.randomUUID(), grade, siteId, "REVIEWED", true, Instant.now());
    }
}
