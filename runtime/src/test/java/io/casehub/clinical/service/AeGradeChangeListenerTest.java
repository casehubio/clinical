package io.casehub.clinical.service;

import io.casehub.clinical.api.AeGradeChangedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.cbr.AeTrajectoryAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

class AeGradeChangeListenerTest {

    @Nested
    class EscalationListenerTest {
        private AeEscalationCaseService escalationService;
        private AeGradeChangeEscalationListener listener;

        @BeforeEach
        void setUp() {
            escalationService = mock(AeEscalationCaseService.class);
            listener = new AeGradeChangeEscalationListener();
            listener.escalationService = escalationService;
        }

        @Test
        void upgrade_callsStartEscalation() {
            var event = event(CtcaeGrade.GRADE_1, CtcaeGrade.GRADE_3);
            listener.onGradeChanged(event);
            verify(escalationService).startEscalationForRegrade(
                event.aeId(), event.enrollmentId(), event.siteId(), event.newGrade(), event.tenantId());
        }

        @Test
        void downgrade_skips() {
            listener.onGradeChanged(event(CtcaeGrade.GRADE_3, CtcaeGrade.GRADE_1));
            verifyNoInteractions(escalationService);
        }

        @Test
        void sameGrade_skips() {
            listener.onGradeChanged(event(CtcaeGrade.GRADE_2, CtcaeGrade.GRADE_2));
            verifyNoInteractions(escalationService);
        }
    }

    @Nested
    class SusarListenerTest {
        private SusarOversightCaseService susarService;
        private AeGradeChangeSusarListener listener;

        @BeforeEach
        void setUp() {
            susarService = mock(SusarOversightCaseService.class);
            listener = new AeGradeChangeSusarListener();
            listener.susarOversightCaseService = susarService;
        }

        @Test
        void upgrade_callsReevaluate() {
            var event = event(CtcaeGrade.GRADE_2, CtcaeGrade.GRADE_4);
            listener.onGradeChanged(event);
            verify(susarService).reevaluateForRegrade(event.aeId(), event.siteId(), event.tenantId());
        }

        @Test
        void downgrade_skips() {
            listener.onGradeChanged(event(CtcaeGrade.GRADE_4, CtcaeGrade.GRADE_2));
            verifyNoInteractions(susarService);
        }
    }

    @Nested
    class RegulatoryListenerTest {
        private RegulatorySubmissionCaseService regulatoryService;
        private AeGradeChangeRegulatoryListener listener;

        @BeforeEach
        void setUp() {
            regulatoryService = mock(RegulatorySubmissionCaseService.class);
            listener = new AeGradeChangeRegulatoryListener();
            listener.regulatorySubmissionCaseService = regulatoryService;
        }

        @Test
        void upgrade_callsReevaluate() {
            var event = event(CtcaeGrade.GRADE_1, CtcaeGrade.GRADE_3);
            listener.onGradeChanged(event);
            verify(regulatoryService).reevaluateForRegrade(event.aeId(), event.siteId(), event.tenantId());
        }

        @Test
        void downgrade_skips() {
            listener.onGradeChanged(event(CtcaeGrade.GRADE_3, CtcaeGrade.GRADE_1));
            verifyNoInteractions(regulatoryService);
        }
    }

    @Nested
    class TrajectoryListenerTest {
        private AeTrajectoryAlertService trajectoryService;
        private AeGradeChangeTrajectoryListener listener;

        @BeforeEach
        void setUp() {
            trajectoryService = mock(AeTrajectoryAlertService.class);
            listener = new AeGradeChangeTrajectoryListener();
            listener.aeTrajectoryAlertService = trajectoryService;
        }

        @Test
        void upgrade_callsEvaluate() {
            var event = event(CtcaeGrade.GRADE_1, CtcaeGrade.GRADE_3);
            listener.onGradeChanged(event);
            verify(trajectoryService).evaluate(event.aeId(), event.tenantId());
        }

        @Test
        void downgrade_alsoCallsEvaluate() {
            var event = event(CtcaeGrade.GRADE_3, CtcaeGrade.GRADE_1);
            listener.onGradeChanged(event);
            verify(trajectoryService).evaluate(event.aeId(), event.tenantId());
        }

        @Test
        void evaluateException_swallowed() {
            var event = event(CtcaeGrade.GRADE_1, CtcaeGrade.GRADE_3);
            when(trajectoryService.evaluate(any(), any())).thenThrow(new RuntimeException("test"));
            listener.onGradeChanged(event);
        }
    }

    private static AeGradeChangedEvent event(CtcaeGrade prev, CtcaeGrade next) {
        return new AeGradeChangedEvent(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), prev, next, Instant.now(), "dr-test", "default");
    }
}
