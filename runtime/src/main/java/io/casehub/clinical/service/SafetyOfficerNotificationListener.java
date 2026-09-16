package io.casehub.clinical.service;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.SafetyOfficerNotificationRequest;
import io.casehub.clinical.api.SafetyOfficerNotifier;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSite;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SafetyOfficerNotificationListener {

    @Inject SafetyOfficerNotifier notifier;
    @Inject SafetyOfficerNotificationLedgerWriter ledgerWriter;
    @Inject
            EntityManager                         em;


    @Transactional
    public void onAeReported(@ObservesAsync final AdverseEventReportedEvent event) {
        try {
            if (event.siteId() == null) {
                Log.errorf("AE %s has no siteId — safety officer notification skipped", event.aeId());
                try {
                    ledgerWriter.writeSkippedEntry(event.aeId(), event.enrollmentId(), null, event.grade(), "safety-officer-notifier-skipped-no-site-id");
                } catch (Exception writeEx) {
                    Log.errorf(writeEx, "AUDIT GAP: could not write skipped entry for AE %s", event.aeId());
                }
                return;
            }
            final TrialSite site = em.find(TrialSite.class, event.siteId());
            if (site == null) {
                Log.warnf("TrialSite %s not found — safety officer notification skipped", event.siteId());
                try {
                    ledgerWriter.writeSkippedEntry(event.aeId(), event.enrollmentId(), event.siteId(), event.grade(), "safety-officer-notifier-skipped-site-not-found");
                } catch (Exception writeEx) {
                    Log.errorf(writeEx, "AUDIT GAP: could not write skipped entry for AE %s", event.aeId());
                }
                return;
            }
            final ClinicalTrial trial = em.find(ClinicalTrial.class, site.trialId);
            if (trial == null) {
                Log.warnf("Trial %s not found — safety officer notification skipped", site.trialId);
                try {
                    ledgerWriter.writeSkippedEntry(event.aeId(), event.enrollmentId(), event.siteId(), event.grade(), "safety-officer-notifier-skipped-trial-not-found");
                } catch (Exception writeEx) {
                    Log.errorf(writeEx, "AUDIT GAP: could not write skipped entry for AE %s", event.aeId());
                }
                return;
            }
            if (trial.safetyOfficerConnectorId == null || trial.safetyOfficerDestination == null) {
                Log.warnf("Trial %s has incomplete safety officer notification config (connectorId=%s, destination=%s) — skipping",
                    site.trialId, trial.safetyOfficerConnectorId, trial.safetyOfficerDestination);
                try {
                    ledgerWriter.writeSkippedEntry(event.aeId(), event.enrollmentId(), event.siteId(), event.grade(), "safety-officer-notifier-skipped-no-config");
                } catch (Exception writeEx) {
                    Log.errorf(writeEx, "AUDIT GAP: could not write skipped entry for AE %s", event.aeId());
                }
                return;
            }
            notifier.notify(new SafetyOfficerNotificationRequest(
                event.aeId(), event.enrollmentId(), event.siteId(), event.grade(),
                trial.safetyOfficerConnectorId, trial.safetyOfficerDestination));
        } catch (Exception e) {
            Log.errorf(e, "Unexpected error in safety officer notification for AE %s — writing failed ledger entry", event.aeId());
            try {
                ledgerWriter.writeObserverFailureEntry(event.aeId(), event.enrollmentId(), event.siteId(), event.grade());
            } catch (Exception writeEx) {
                Log.errorf(writeEx, "AUDIT GAP: could not write observer failure entry for AE %s", event.aeId());
            }
        }
    }
}
