package io.casehub.clinical.service;

import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Updates ae.regulatorySubmissionStatus = FILED when the regulatory-submission case
 * reaches GoalReached or CaseCompleted.
 *
 * <p>Discriminates via DB lookup — CaseLifecycleEvent carries no case name or namespace.
 * Guard: only processes if status == PENDING (protects against DEADLINE_MISSED being
 * overwritten and CDI at-least-once re-delivery).
 */
@ApplicationScoped
public class RegulatorySubmissionCompletedListener {

    private static final Logger LOG = Logger.getLogger(RegulatorySubmissionCompletedListener.class);

    @Inject RegulatorySubmissionLedgerWriter ledgerWriter;

    public void onCaseLifecycleEvent(@ObservesAsync CaseLifecycleEvent event) {
        if (!"GoalReached".equals(event.eventType()) && !"CaseCompleted".equals(event.eventType())) {
            return;
        }
        markFiled(event.caseId());
    }

    @Transactional
    void markFiled(UUID caseId) {
        AdverseEvent ae = AdverseEvent.find("regulatorySubmissionCaseId", caseId).firstResult();
        if (ae == null) {
            return;
        }
        if (ae.regulatorySubmissionStatus != RegulatorySubmissionStatus.PENDING) {
            LOG.debugf("RegulatorySubmissionCompletedListener: caseId=%s status=%s — skipping (not PENDING)",
                    caseId, ae.regulatorySubmissionStatus);
            return;
        }
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.FILED;
        ledgerWriter.writeFiledEntry(ae);
        LOG.infof("RegulatorySubmissionCompletedListener: aeId=%s set FILED", ae.id);
    }
}
