package io.casehub.clinical.cbr;

import io.casehub.clinical.api.AeTrajectoryAlertEvent;
import io.casehub.clinical.api.SiteEnrollmentAlertEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrajectoryAlertListenerTest {

    private Connector connector;
    private TrajectoryAlertListener listener;

    @BeforeEach
    void setUp() {
        connector = Mockito.mock(Connector.class);
        when(connector.id()).thenReturn("slack");
        when(connector.send(any())).thenReturn(true);
        listener = new TrajectoryAlertListener(List.of(connector), "slack", "#safety-alerts");
    }

    @Test
    void aeAlert_sends_notification_with_predicted_outcome() {
        var event = new AeTrajectoryAlertEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                CtcaeGrade.GRADE_4, 3, 0.85,
                "COMPLETED", 0.75, "trace-1", "default");

        listener.onAeTrajectoryAlert(event);

        ArgumentCaptor<ConnectorMessage> captor = ArgumentCaptor.forClass(ConnectorMessage.class);
        verify(connector).send(captor.capture());
        ConnectorMessage msg = captor.getValue();
        assertThat(msg.destination()).isEqualTo("#safety-alerts");
        assertThat(msg.title()).contains("Trajectory Alert").contains("GRADE_4");
        assertThat(msg.body()).contains("COMPLETED");
        assertThat(msg.body()).contains("75.0%");
    }

    @Test
    void aeAlert_skips_when_no_matching_connector() {
        listener = new TrajectoryAlertListener(List.of(connector), "email", "#safety-alerts");

        var event = new AeTrajectoryAlertEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            CtcaeGrade.GRADE_3, 2, 0.6,
            "COMPLETED", 0.5, "trace-2", "default");

        listener.onAeTrajectoryAlert(event);

        verify(connector, never()).send(any());
    }

    @Test
    void siteAlert_sends_notification() {
        var event = new SiteEnrollmentAlertEvent(
            UUID.randomUUID(), UUID.randomUUID(),
            3, 0.9, "SLOW_ENROLLMENT", 0.8, "trace-3", "default");

        listener.onSiteEnrollmentAlert(event);

        ArgumentCaptor<ConnectorMessage> captor = ArgumentCaptor.forClass(ConnectorMessage.class);
        verify(connector).send(captor.capture());
        ConnectorMessage msg = captor.getValue();
        assertThat(msg.title()).contains("Enrollment Alert");
        assertThat(msg.body()).contains("SLOW_ENROLLMENT");
    }

    @Test
    void aeAlert_continues_on_connector_failure() {
        when(connector.send(any())).thenThrow(new RuntimeException("network error"));

        var event = new AeTrajectoryAlertEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            CtcaeGrade.GRADE_4, 3, 0.85,
            "COMPLETED", 0.75, "trace-4", "default");

        // Should not throw
        listener.onAeTrajectoryAlert(event);
    }
}
