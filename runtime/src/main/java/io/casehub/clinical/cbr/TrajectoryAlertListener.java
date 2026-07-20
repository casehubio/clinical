package io.casehub.clinical.cbr;

import io.casehub.clinical.api.AeTrajectoryAlertEvent;
import io.casehub.clinical.api.SiteEnrollmentAlertEvent;
import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Consumes trajectory alert events and delivers notifications via configured connector.
 *
 * <p>Handles both AE trajectory alerts (grade escalation pattern matching) and site
 * enrollment alerts (enrollment rate anomaly detection). Notifications are delivered
 * via the configured connector (e.g., Slack) to a destination channel.
 *
 * <p>Connector/destination are configurable — defaults to Slack {@code #safety-alerts}.
 * If no matching connector is found, the alert is logged but not delivered.
 */
@ApplicationScoped
public class TrajectoryAlertListener {

    private static final Logger LOG = Logger.getLogger(TrajectoryAlertListener.class);

    private final Iterable<Connector> connectors;
    private final String connectorId;
    private final String destination;

    @Inject
    public TrajectoryAlertListener(@Any Instance<Connector> connectors,
                                    @ConfigProperty(name = "casehub.clinical.trajectory-alert.connector-id",
                                                    defaultValue = "slack") String connectorId,
                                    @ConfigProperty(name = "casehub.clinical.trajectory-alert.destination",
                                                    defaultValue = "#safety-alerts") String destination) {
        this.connectors = connectors;
        this.connectorId = connectorId;
        this.destination = destination;
    }

    TrajectoryAlertListener(Iterable<Connector> connectors, String connectorId, String destination) {
        this.connectors = connectors;
        this.connectorId = connectorId;
        this.destination = destination;
    }


    @Transactional
    public void onAeTrajectoryAlert(@ObservesAsync AeTrajectoryAlertEvent event) {
        try {
            updateAeFlags(event);
        } catch (Exception e) {
            LOG.warnf(e, "AE trajectory flag update failed for AE %s — notification proceeds", event.aeId());
        }

        try {
            String title = "AE Trajectory Alert — %s".formatted(event.currentGrade());
            String body = ("AE %s: %d trajectory matches (top score: %.2f). " +
                           "Predicted outcome: %s (%.1f%% probability). Trace: %s")
                                  .formatted(event.aeId(), event.matchCount(), event.topScore(),
                                             event.predictedOutcome(), event.predictedProbability() * 100,
                                             event.traceId());

            sendNotification(title, body);
            LOG.infof("AE trajectory alert delivered for AE %s: %s → %.1f%%",
                      event.aeId(), event.predictedOutcome(), event.predictedProbability() * 100);
        } catch (Exception e) {
            LOG.errorf(e, "AE trajectory alert notification failed for AE %s", event.aeId());
        }
    }

    private void updateAeFlags(AeTrajectoryAlertEvent event) {
        io.casehub.clinical.entity.AdverseEvent ae =
                io.casehub.clinical.entity.AdverseEvent.findById(event.aeId());
        if (ae != null) {
            ae.trajectoryMatchCount       = event.matchCount();
            ae.trajectoryPredictedOutcome = event.predictedOutcome();
        }
    }

    @Transactional
    public void onSiteEnrollmentAlert(@ObservesAsync SiteEnrollmentAlertEvent event) {
        try {
            String title = "Enrollment Alert — Site %s".formatted(event.siteId());
            String body = ("Site %s (trial %s): %d enrollment matches (top score: %.2f). " +
                "Predicted outcome: %s (%.1f%% probability). Trace: %s")
                .formatted(event.siteId(), event.trialId(), event.matchCount(), event.topScore(),
                    event.predictedOutcome(), event.predictedProbability() * 100,
                    event.traceId());

            sendNotification(title, body);
            LOG.infof("Site enrollment alert delivered for site %s: %s → %.1f%%",
                event.siteId(), event.predictedOutcome(), event.predictedProbability() * 100);
        } catch (Exception e) {
            LOG.errorf(e, "Site enrollment alert notification failed for site %s", event.siteId());
        }
    }

    private void sendNotification(String title, String body) {
        Optional<Connector> connector = StreamSupport.stream(connectors.spliterator(), false)
            .filter(c -> connectorId.equals(c.id()))
            .findFirst();
        if (connector.isEmpty()) {
            LOG.warnf("No connector '%s' found — trajectory alert not delivered: %s", connectorId, title);
            return;
        }
        connector.get().send(new ConnectorMessage(destination, title, body));
    }
}
