package io.casehub.clinical.cbr;

import io.casehub.clinical.api.TrialStatusChangedEvent;
import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class TrialCompletionSiteTrajectoryWriter {

    private static final Logger LOG = Logger.getLogger(TrialCompletionSiteTrajectoryWriter.class);

    @Inject ClinicalCbrService cbrService;
    @Inject SiteEnrollmentTrajectoryBuilder trajectoryBuilder;

    @Transactional
    public void onTrialStatusChanged(@ObservesAsync TrialStatusChangedEvent event) {
        if (event.newStatus() != TrialStatus.COMPLETED && event.newStatus() != TrialStatus.TERMINATED) return;

        ClinicalTrial trial = ClinicalTrial.findById(event.trialId());
        if (trial == null) return;

        List<TrialSite> sites = TrialSite.find("trialId", event.trialId()).list();
        for (TrialSite site : sites) {
            try {
                storeForSite(site, trial, event.tenantId());
            } catch (Exception e) {
                LOG.warnf(e, "Failed to store enrollment trajectory for siteId=%s", site.id);
            }
        }
        LOG.infof("Stored enrollment trajectories for %d sites in trial %s", sites.size(), event.trialId());
    }

    @SuppressWarnings("unchecked")
    private void storeForSite(TrialSite site, ClinicalTrial trial, String tenantId) {
        Instant trialActivatedAt = PatientEnrollment.<PatientEnrollment>find(
                "siteId = ?1 AND tenantId = ?2 AND enrolledAt IS NOT NULL ORDER BY enrolledAt ASC",
                site.id, tenantId)
                .firstResultOptional()
                .map(e -> e.enrolledAt)
                .orElse(null);
        if (trialActivatedAt == null) return;

        var trajectory = trajectoryBuilder.buildTrajectory(site.id, trial.id, trialActivatedAt, tenantId);
        if (trajectory.isEmpty()) return;

        Map<String, Object> features = Map.of(
                "trialPhase", trial.phase != null ? trial.phase.name() : "UNKNOWN",
                "enrollmentRate", trajectory);

        String problem = "Site " + site.id +
                " in " + (trial.phase != null ? trial.phase.name() : "UNKNOWN") + " trial";
        String solution = "Enrollment: " + trajectory.size() + " weeks tracked";

        var cbrCase = new PlanCbrCase(problem, solution, "COMPLETED", 1.0,
                FeatureValue.toFeatureMap(features), List.of(),
                null, null);

        cbrService.storeIdempotent(cbrCase, "clinical-site-enrollment", site.id.toString(),
                ClinicalCbrDomains.SITE_ENROLLMENT, tenantId, null);
    }
}
