package io.casehub.clinical.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProtocolAmendmentListener {

    private static final Logger LOG = Logger.getLogger(ProtocolAmendmentListener.class);

    @Inject ProtocolAmendmentStatusUpdater statusUpdater;

    public void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        if (!"GoalReached".equals(event.eventType()) && !"CaseCompleted".equals(event.eventType())) return;

        JsonNode snapshot = event.contextSnapshot();
        if (snapshot == null) return;

        String amendmentIdStr = snapshot.path("amendmentId").asText(null);
        if (amendmentIdStr == null) return;

        UUID amendmentId;
        try {
            amendmentId = UUID.fromString(amendmentIdStr);
        } catch (IllegalArgumentException e) {
            LOG.warnf("ProtocolAmendmentListener: invalid amendmentId: %s", amendmentIdStr);
            return;
        }

        String recommendation = snapshot.path("advisorRecommendation").asText(null);
        if (recommendation == null) {
            LOG.errorf("ProtocolAmendmentListener: advisorRecommendation absent from case context for amendmentId=%s " +
                "— amendment stays at current status; audit gap", amendmentId);
            return;
        }

        statusUpdater.applyRecommendation(amendmentId, recommendation);
    }
}
