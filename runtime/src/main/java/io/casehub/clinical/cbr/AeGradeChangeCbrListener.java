package io.casehub.clinical.cbr;

import io.casehub.clinical.api.AeGradeChangedEvent;
import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AeGradeChangeCbrListener {

    private static final Logger LOG = Logger.getLogger(AeGradeChangeCbrListener.class);

    private final AeCbrCaseBuilder caseBuilder;

    @Inject
    public AeGradeChangeCbrListener(AeCbrCaseBuilder caseBuilder) {
        this.caseBuilder = caseBuilder;
    }

    @Transactional
    public void onGradeChanged(@ObservesAsync AeGradeChangedEvent event) {
        try {
            AdverseEvent ae = AdverseEvent.findById(event.aeId());
            if (ae == null) {
                LOG.warnf("AE not found for CBR re-store: %s", event.aeId());
                return;
            }
            onGradeChanged(event, ae);
        } catch (Exception e) {
            LOG.errorf(e, "CBR re-store failed for AE %s — stale CBR case may persist", event.aeId());
        }
    }

    void onGradeChanged(AeGradeChangedEvent event, AdverseEvent ae) {
        if (ae.escalationStatus != AeEscalationStatus.COMPLETED) return;

        PatientEnrollment enrollment = ae.enrollmentId != null
            ? PatientEnrollment.findById(ae.enrollmentId) : null;
        TrialSite site = enrollment != null && enrollment.siteId != null
            ? TrialSite.findById(enrollment.siteId) : null;
        ClinicalTrial trial = site != null && site.trialId != null
            ? ClinicalTrial.findById(site.trialId) : null;

        caseBuilder.buildAndStore(ae, enrollment, site, trial,
            null, false, "regrade", ae.engineCaseId, event.tenantId());

        LOG.infof("CBR case re-stored for AE %s after grade change %s->%s",
            ae.id, event.previousGrade(), event.newGrade());
    }
}
