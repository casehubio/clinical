package io.casehub.clinical.service;

import io.casehub.clinical.api.AeGradeChangedEvent;
import io.casehub.clinical.cbr.AeTrajectoryAlertService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AeGradeChangeTrajectoryListener {

    private static final Logger LOG = Logger.getLogger(AeGradeChangeTrajectoryListener.class);

    @Inject AeTrajectoryAlertService aeTrajectoryAlertService;

    public void onGradeChanged(@ObservesAsync AeGradeChangedEvent event) {
        try {
            aeTrajectoryAlertService.evaluate(event.aeId(), event.tenantId());
        } catch (Exception e) {
            LOG.warnf(e, "Trajectory alert re-evaluation failed for aeId=%s", event.aeId());
        }
    }
}
