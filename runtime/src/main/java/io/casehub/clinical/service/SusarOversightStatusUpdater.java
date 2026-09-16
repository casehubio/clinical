package io.casehub.clinical.service;

import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.entity.AdverseEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Writes SusarOversightStatus.COMPLETED to AdverseEvent in REQUIRES_NEW.
 * Separate from SusarOversightListener so the Panache call can be mocked
 * in unit tests, and the status write commits independently of the caller.
 */
@ApplicationScoped
public class SusarOversightStatusUpdater {

    private static final Logger LOG = Logger.getLogger(SusarOversightStatusUpdater.class);
    @Inject
                         EntityManager em;


    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean markCompleted(UUID aeId) {
        AdverseEvent ae = em.find(AdverseEvent.class, aeId);
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
