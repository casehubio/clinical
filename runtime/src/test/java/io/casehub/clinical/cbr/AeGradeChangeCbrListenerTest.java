package io.casehub.clinical.cbr;

import io.casehub.clinical.api.AeGradeChangedEvent;
import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.entity.AdverseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AeGradeChangeCbrListenerTest {

    private AeCbrCaseBuilder caseBuilder;
    private AeGradeChangeCbrListener listener;

    private UUID aeId;
    private UUID enrollmentId;
    private UUID siteId;
    private UUID engineCaseId;

    @BeforeEach
    void setUp() {
        caseBuilder = mock(AeCbrCaseBuilder.class);
        listener = new AeGradeChangeCbrListener(caseBuilder);

        aeId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        engineCaseId = UUID.randomUUID();
    }

    @Test
    void onGradeChanged_completedStatus_callsBuildAndStore() {
        AdverseEvent ae = makeAe(AeEscalationStatus.COMPLETED);
        var event = new AeGradeChangedEvent(aeId, enrollmentId, siteId,
            CtcaeGrade.GRADE_3, CtcaeGrade.GRADE_4, Instant.now(), "dr-test", "default");

        listener.onGradeChanged(event, ae);

        verify(caseBuilder).buildAndStore(eq(ae), any(), any(), any(),
            isNull(), eq(false), eq("regrade"), eq(engineCaseId), eq("default"));
    }

    @Test
    void onGradeChanged_requestedStatus_skips() {
        AdverseEvent ae = makeAe(AeEscalationStatus.REQUESTED);
        var event = new AeGradeChangedEvent(aeId, enrollmentId, siteId,
            CtcaeGrade.GRADE_1, CtcaeGrade.GRADE_3, Instant.now(), "dr-test", "default");

        listener.onGradeChanged(event, ae);

        verifyNoInteractions(caseBuilder);
    }

    @Test
    void onGradeChanged_noneStatus_skips() {
        AdverseEvent ae = makeAe(AeEscalationStatus.NONE);
        var event = new AeGradeChangedEvent(aeId, enrollmentId, siteId,
            CtcaeGrade.GRADE_1, CtcaeGrade.GRADE_2, Instant.now(), "dr-test", "default");

        listener.onGradeChanged(event, ae);

        verifyNoInteractions(caseBuilder);
    }

    @Test
    void onGradeChanged_failedStatus_skips() {
        AdverseEvent ae = makeAe(AeEscalationStatus.FAILED);
        var event = new AeGradeChangedEvent(aeId, enrollmentId, siteId,
            CtcaeGrade.GRADE_3, CtcaeGrade.GRADE_4, Instant.now(), "dr-test", "default");

        listener.onGradeChanged(event, ae);

        verifyNoInteractions(caseBuilder);
    }

    @Test
    void onGradeChanged_downgrade_callsBuildAndStore() {
        AdverseEvent ae = makeAe(AeEscalationStatus.COMPLETED);
        var event = new AeGradeChangedEvent(aeId, enrollmentId, siteId,
            CtcaeGrade.GRADE_4, CtcaeGrade.GRADE_1, Instant.now(), "dr-test", "default");

        listener.onGradeChanged(event, ae);

        verify(caseBuilder).buildAndStore(eq(ae), any(), any(), any(),
            isNull(), eq(false), eq("regrade"), eq(engineCaseId), eq("default"));
    }

    private AdverseEvent makeAe(AeEscalationStatus status) {
        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.enrollmentId = null;
        ae.grade = CtcaeGrade.GRADE_4;
        ae.eventType = "NAUSEA";
        ae.escalationStatus = status;
        ae.engineCaseId = status == AeEscalationStatus.COMPLETED ? engineCaseId : null;
        ae.tenantId = "default";
        return ae;
    }
}
