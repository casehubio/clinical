package io.casehub.clinical.service;

import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Observes CaseLifecycleEvent and marks SUSAR oversight cases COMPLETED.
 *
 * <p>Discriminates by {@code susarOversight} key in case context — set by
 * SusarOversightCaseService in the initial context. GoalReached fires reliably
 * in-memory tests (engine#393); CaseCompleted is also accepted as a fallback.
 */
@ApplicationScoped
public class SusarOversightListener {

    private static final Logger LOG = Logger.getLogger(SusarOversightListener.class);
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(5);

    @Inject CaseInstanceRepository caseInstanceRepository;
    @Inject SusarOversightStatusUpdater statusUpdater;

    @Transactional
    public void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        if (!"GoalReached".equals(event.eventType()) && !"CaseCompleted".equals(event.eventType())) return;

        var instance = caseInstanceRepository
                .findByUuid(event.caseId(), event.tenancyId())
                .await().atMost(LOOKUP_TIMEOUT);
        if (instance == null) return;

        // Discriminator — only SUSAR oversight cases carry this key
        if (instance.getCaseContext().getPath("susarOversight") == null) return;

        Object aeIdObj = instance.getCaseContext().getPath("aeId");
        if (aeIdObj == null) {
            LOG.warnf("SusarOversightListener: susarOversight case has no aeId: caseId=%s", event.caseId());
            return;
        }
        UUID aeId;
        try {
            aeId = UUID.fromString(aeIdObj.toString());
        } catch (IllegalArgumentException e) {
            LOG.warnf("SusarOversightListener: invalid aeId in case context: %s", aeIdObj);
            return;
        }

        boolean first = statusUpdater.markCompleted(aeId);
        if (!first) return;
        LOG.infof("SusarOversightListener: susarOversightStatus=COMPLETED for aeId=%s caseId=%s",
                aeId, event.caseId());
    }
}
