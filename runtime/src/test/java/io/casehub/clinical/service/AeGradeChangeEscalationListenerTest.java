package io.casehub.clinical.service;

import static org.mockito.Mockito.*;

import io.casehub.clinical.api.AeGradeChangedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AeGradeChangeEscalationListenerTest {

    @Mock AeEscalationCaseService escalationService;
    @InjectMocks AeGradeChangeEscalationListener listener;

    @Test
    void grade1_to_grade2_upgrade_does_not_call_escalation() {
        listener.onGradeChanged(gradeChanged(CtcaeGrade.GRADE_1, CtcaeGrade.GRADE_2));
        verifyNoInteractions(escalationService);
    }

    @Test
    void grade2_to_grade3_upgrade_calls_escalation() {
        var event = gradeChanged(CtcaeGrade.GRADE_2, CtcaeGrade.GRADE_3);
        listener.onGradeChanged(event);
        verify(escalationService).startEscalationForRegrade(
            event.aeId(), event.enrollmentId(), event.siteId(), event.newGrade(), event.tenantId());
    }

    @Test
    void grade3_to_grade4_upgrade_calls_escalation() {
        var event = gradeChanged(CtcaeGrade.GRADE_3, CtcaeGrade.GRADE_4);
        listener.onGradeChanged(event);
        verify(escalationService).startEscalationForRegrade(
            event.aeId(), event.enrollmentId(), event.siteId(), event.newGrade(), event.tenantId());
    }

    @Test
    void grade4_to_grade5_upgrade_calls_escalation() {
        var event = gradeChanged(CtcaeGrade.GRADE_4, CtcaeGrade.GRADE_5);
        listener.onGradeChanged(event);
        verify(escalationService).startEscalationForRegrade(
            event.aeId(), event.enrollmentId(), event.siteId(), event.newGrade(), event.tenantId());
    }

    @Test
    void downgrade_does_not_call_escalation() {
        listener.onGradeChanged(gradeChanged(CtcaeGrade.GRADE_4, CtcaeGrade.GRADE_2));
        verifyNoInteractions(escalationService);
    }

    private AeGradeChangedEvent gradeChanged(CtcaeGrade from, CtcaeGrade to) {
        return new AeGradeChangedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            from, to, Instant.now(), "test-user", "test-tenant");
    }
}
