package io.casehub.clinical.service;

import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.entity.AdverseEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Writes SusarOversightStatus.COMPLETED to AdverseEvent in REQUIRES_NEW.
 * Separate from SusarOversightListener so the Panache call can be mocked
 * in unit tests, and the status write commits independently of the caller.
 */
@ApplicationScoped
public class SusarOversightStatusUpdater {

    private static final Logger LOG = Logger.getLogger(SusarOversightStatusUpdater.class);

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean markCompleted(UUID aeId) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        if (ae == null) {
            LOG.warnf("SusarOversightStatusUpdater: AE not found for aeId=%s", aeId);
            return false;
        }
        if (ae.susarOversightStatus == SusarOversightStatus.COMPLETED) {
            LOG.debugf("SusarOversightStatusUpdater: aeId=%s already COMPLETED — skipping", aeId);
            return false;
        }
        ae.susarOversightStatus = SusarOversightStatus.COMPLETED;
        return true;
    }
}
