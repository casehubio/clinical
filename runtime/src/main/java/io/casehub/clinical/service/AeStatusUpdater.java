package io.casehub.clinical.service;

import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.entity.AdverseEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Writes AE escalation status back to the AdverseEvent entity.
 *
 * <p>Separated from AeEscalationListener so the Panache call can be mocked
 * in Mockito unit tests, and so the status write uses REQUIRES_NEW — committing
 * independently of the surrounding ledger write transaction. This ensures
 * escalationStatus=COMPLETED is persisted even if the ledger write fails.
 */
@ApplicationScoped
public class AeStatusUpdater {

    private static final Logger LOG = Logger.getLogger(AeStatusUpdater.class);

    /**
     * Sets escalationStatus to COMPLETED if not already set.
     * Returns true if the status was newly set; false if already COMPLETED (idempotent).
     * Uses REQUIRES_NEW so the commit survives even if the caller's transaction rolls back.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean markCompleted(UUID aeId) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        if (ae == null) {
            LOG.warnf("AeStatusUpdater: AdverseEvent not found for aeId=%s — status not updated", aeId);
            return false;
        }
        if (ae.escalationStatus == AeEscalationStatus.COMPLETED) {
            LOG.debugf("AeStatusUpdater: aeId=%s already COMPLETED — skipping", aeId);
            return false;
        }
        ae.escalationStatus = AeEscalationStatus.COMPLETED;
        LOG.infof("AeStatusUpdater: escalationStatus set to COMPLETED for aeId=%s", aeId);
        return true;
    }
}
