package io.casehub.clinical.service;

import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.entity.AdverseEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.UUID;

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


    public enum CompletionResult {
        COMPLETED,
        ALREADY_COMPLETED,
        SUPERSEDED,
        NOT_FOUND
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public CompletionResult markCompleted(UUID aeId, UUID expectedCaseId) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        if (ae == null) {
            LOG.warnf("AeStatusUpdater: AdverseEvent not found for aeId=%s — status not updated", aeId);
            return CompletionResult.NOT_FOUND;
        }
        if (ae.escalationStatus == AeEscalationStatus.COMPLETED) {
            LOG.debugf("AeStatusUpdater: aeId=%s already COMPLETED — skipping", aeId);
            return CompletionResult.ALREADY_COMPLETED;
        }
        if (!expectedCaseId.equals(ae.engineCaseId)) {
            LOG.infof("Superseded escalation case %s completed for aeId=%s — current case is %s",
                expectedCaseId, aeId, ae.engineCaseId);
            return CompletionResult.SUPERSEDED;
        }
        ae.escalationStatus = AeEscalationStatus.COMPLETED;
        LOG.infof("AeStatusUpdater: escalationStatus set to COMPLETED for aeId=%s", aeId);
        return CompletionResult.COMPLETED;
    }
}
