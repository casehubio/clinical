package io.casehub.clinical.service;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class DsmbBatchSignalNotifier {

    private static final Logger LOG = Logger.getLogger(DsmbBatchSignalNotifier.class);

    private final Map<String, Connector> connectorRegistry;
    private final DsmbBatchSignalNotificationLedgerWriter ledgerWriter;
    private final String connectorId;
    private final String channel;

    @Inject
    DsmbBatchSignalNotifier(
            @All List<Connector> connectors,
            DsmbBatchSignalNotificationLedgerWriter ledgerWriter,
            @ConfigProperty(name = "casehub.clinical.dsmb.batch-signal.connector-id",
                            defaultValue = "slack") String connectorId,
            @ConfigProperty(name = "casehub.clinical.dsmb.batch-signal.notification-channel",
                            defaultValue = "dsmb") String channel) {
        this.connectorRegistry = connectors.stream()
            .collect(Collectors.toMap(Connector::id, Function.identity()));
        this.ledgerWriter = ledgerWriter;
        this.connectorId = connectorId;
        this.channel = channel;
    }

    public void notify(UUID trialId, String signalType, String summary,
                       int affectedSiteCount, UUID workItemId) {
        Connector connector = connectorRegistry.get(connectorId);
        if (connector == null) {
            LOG.warnf("No connector '%s' found — DSMB batch signal notification skipped for trial %s",
                connectorId, trialId);
            ledgerWriter.writeFailure(trialId, signalType, workItemId,
                connectorId, channel, "No connector: " + connectorId);
            return;
        }
        try {
            connector.send(new ConnectorMessage(
                channel,
                "DSMB Batch Signal: " + signalType,
                "%s\nAffected sites: %d\nTrial: %s\nWorkItem: %s"
                    .formatted(summary, affectedSiteCount, trialId, workItemId)));
            ledgerWriter.writeSuccess(trialId, signalType, workItemId, connectorId, channel);
        } catch (Exception e) {
            LOG.warnf(e, "DSMB batch signal notification failed for trial %s", trialId);
            ledgerWriter.writeFailure(trialId, signalType, workItemId,
                connectorId, channel, e.getMessage());
        }
    }
}
