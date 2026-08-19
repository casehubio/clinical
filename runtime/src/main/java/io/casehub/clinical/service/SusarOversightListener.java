package io.casehub.clinical.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Observes CaseLifecycleEvent and marks SUSAR oversight cases COMPLETED.
 *
 * <p>Discriminates by {@code susarOversight} key in contextSnapshot — set by
 * SusarOversightCaseService in the initial context. GoalReached fires reliably
 * in-memory tests (engine#393); CaseCompleted is also accepted as a fallback.
 */
@ApplicationScoped
public class SusarOversightListener {

    private static final Logger LOG = Logger.getLogger(SusarOversightListener.class);

    @Inject SusarOversightStatusUpdater statusUpdater;

    public void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        if (!"GoalReached".equals(event.eventType()) && !"CaseCompleted".equals(event.eventType())) return;

        JsonNode snapshot = event.contextSnapshot();
        if (snapshot == null) return;

        if (snapshot.path("susarOversight").isMissingNode()) return;

        String aeIdStr = snapshot.path("aeId").asText(null);
        if (aeIdStr == null) {
            LOG.warnf("SusarOversightListener: susarOversight case has no aeId: caseId=%s", event.caseId());
            return;
        }
        UUID aeId;
        try {
            aeId = UUID.fromString(aeIdStr);
        } catch (IllegalArgumentException e) {
            LOG.warnf("SusarOversightListener: invalid aeId in case context: %s", aeIdStr);
            return;
        }

        boolean first = statusUpdater.markCompleted(aeId);
        if (!first) return;
        LOG.infof("SusarOversightListener: susarOversightStatus=COMPLETED for aeId=%s caseId=%s",
                aeId, event.caseId());
    }
}
