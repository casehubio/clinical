package io.casehub.clinical.service;

import io.casehub.clinical.api.AeGradeChangedEvent;
import io.casehub.clinical.api.SafetyOfficerNotificationRequest;
import io.casehub.clinical.api.SafetyOfficerNotifier;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AeGradeChangeSafetyOfficerListener {

    private static final Logger LOG = Logger.getLogger(AeGradeChangeSafetyOfficerListener.class);

    @Inject SafetyOfficerNotifier notifier;
    @Inject SafetyOfficerNotificationLedgerWriter ledgerWriter;

    @Transactional
    public void onGradeChanged(@ObservesAsync AeGradeChangedEvent event) {
        if (!event.isUpgrade()) return;
        try {
            if (event.siteId() == null) {
                ledgerWriter.writeSkippedEntry(event.aeId(), event.enrollmentId(), null,
                    event.newGrade(), "safety-officer-regrade-skipped-no-site-id");
                return;
            }
            TrialSite site = TrialSite.findById(event.siteId());
            if (site == null) {
                ledgerWriter.writeSkippedEntry(event.aeId(), event.enrollmentId(), event.siteId(),
                    event.newGrade(), "safety-officer-regrade-skipped-site-not-found");
                return;
            }
            ClinicalTrial trial = ClinicalTrial.findById(site.trialId);
            if (trial == null || trial.safetyOfficerConnectorId == null || trial.safetyOfficerDestination == null) {
                ledgerWriter.writeSkippedEntry(event.aeId(), event.enrollmentId(), event.siteId(),
                    event.newGrade(), "safety-officer-regrade-skipped-no-config");
                return;
            }
            notifier.notify(new SafetyOfficerNotificationRequest(
                event.aeId(), event.enrollmentId(), event.siteId(), event.newGrade(),
                trial.safetyOfficerConnectorId, trial.safetyOfficerDestination));
        } catch (Exception e) {
            LOG.errorf(e, "Safety officer regrade notification failed for AE %s", event.aeId());
            try {
                ledgerWriter.writeObserverFailureEntry(event.aeId(), event.enrollmentId(),
                    event.siteId(), event.newGrade());
            } catch (Exception writeEx) {
                LOG.errorf(writeEx, "AUDIT GAP: could not write failure entry for AE %s", event.aeId());
            }
        }
    }
}
