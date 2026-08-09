package io.casehub.clinical.service;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DsmbBatchSignalNotifierTest {

    private Connector slackConnector;
    private DsmbBatchSignalNotificationLedgerWriter ledgerWriter;
    private DsmbBatchSignalNotifier notifier;

    @BeforeEach
    void setup() {
        slackConnector = mock(Connector.class);
        when(slackConnector.id()).thenReturn("slack");
        ledgerWriter = mock(DsmbBatchSignalNotificationLedgerWriter.class);
        notifier = new DsmbBatchSignalNotifier(List.of(slackConnector), ledgerWriter,
            "slack", "dsmb");
    }

    @Test
    void sends_connector_message_and_writes_success_ledger_entry() {
        UUID trialId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        when(slackConnector.send(any())).thenReturn(true);

        notifier.notify(trialId, "GRADE_THRESHOLD", "3 of 10 sites above 10%", 3, workItemId);

        ArgumentCaptor<ConnectorMessage> captor = ArgumentCaptor.forClass(ConnectorMessage.class);
        verify(slackConnector).send(captor.capture());
        ConnectorMessage msg = captor.getValue();
        assertThat(msg.destination()).isEqualTo("dsmb");
        assertThat(msg.title()).contains("GRADE_THRESHOLD");
        assertThat(msg.body()).contains("3 of 10 sites above 10%");
        assertThat(msg.body()).contains(workItemId.toString());

        verify(ledgerWriter).writeSuccess(trialId, "GRADE_THRESHOLD", workItemId, "slack", "dsmb");
    }

    @Test
    void missing_connector_writes_failure_ledger_entry() {
        notifier = new DsmbBatchSignalNotifier(List.of(), ledgerWriter, "slack", "dsmb");
        UUID trialId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();

        notifier.notify(trialId, "CROSS_SITE_CLUSTER", "summary", 4, workItemId);

        verify(slackConnector, never()).send(any());
        verify(ledgerWriter).writeFailure(eq(trialId), eq("CROSS_SITE_CLUSTER"),
            eq(workItemId), eq("slack"), eq("dsmb"), contains("No connector"));
    }

    @Test
    void connector_failure_writes_failure_ledger_entry() {
        UUID trialId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        when(slackConnector.send(any())).thenThrow(new RuntimeException("network error"));

        notifier.notify(trialId, "GRADE_THRESHOLD", "summary", 3, workItemId);

        verify(ledgerWriter).writeFailure(eq(trialId), eq("GRADE_THRESHOLD"),
            eq(workItemId), eq("slack"), eq("dsmb"), contains("network error"));
    }
}
