package io.casehub.clinical.service;

import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProtocolAmendmentListener {

    private static final Logger LOG = Logger.getLogger(ProtocolAmendmentListener.class);
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(5);

    @Inject CaseInstanceRepository caseInstanceRepository;
    @Inject ProtocolAmendmentStatusUpdater statusUpdater;

    public void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        if (!"GoalReached".equals(event.eventType()) && !"CaseCompleted".equals(event.eventType())) return;

        var instance = caseInstanceRepository
            .findByUuid(event.caseId(), event.tenancyId())
            .await().atMost(LOOKUP_TIMEOUT);
        if (instance == null) return;

        Object amendmentIdObj = instance.getCaseContext().getPath("amendmentId");
        if (amendmentIdObj == null) return;

        UUID amendmentId;
        try {
            amendmentId = UUID.fromString(amendmentIdObj.toString());
        } catch (IllegalArgumentException e) {
            LOG.warnf("ProtocolAmendmentListener: invalid amendmentId: %s", amendmentIdObj);
            return;
        }

        Object recObj = instance.getCaseContext().getPath("advisorRecommendation");
        if (recObj == null) {
            LOG.errorf("ProtocolAmendmentListener: advisorRecommendation absent from case context for amendmentId=%s " +
                "— amendment stays at current status; audit gap", amendmentId);
            return;
        }

        statusUpdater.applyRecommendation(amendmentId, recObj.toString());
    }
}
