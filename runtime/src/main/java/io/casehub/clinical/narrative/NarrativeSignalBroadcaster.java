package io.casehub.clinical.narrative;

import io.casehub.blocks.summarisation.narrative.DecisionSignal;
import io.casehub.pages.push.EventBroadcaster;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class NarrativeSignalBroadcaster {

    private static final Logger LOG = Logger.getLogger(NarrativeSignalBroadcaster.class);

    private final EventBroadcaster eventBroadcaster;

    @Inject
    public NarrativeSignalBroadcaster(EventBroadcaster eventBroadcaster) {
        this.eventBroadcaster = eventBroadcaster;
    }

    public void broadcast(DecisionSignal signal) {
        String topic = "clinical:narrative:" + signal.caseId();
        try {
            eventBroadcaster.broadcast(topic, signal);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to broadcast narrative signal for caseId=%s", signal.caseId());
        }
    }
}
