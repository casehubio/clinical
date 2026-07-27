package io.casehub.clinical.cbr;

import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Periodic snapshot of site enrollment trajectories as CBR cases.
 *
 * <p>Runs on a configurable interval (default 24h). For each active trial site with
 * enrolled patients, builds the enrollment trajectory and stores it as a CBR case in
 * {@link ClinicalCbrDomains#SITE_ENROLLMENT}. This enables trajectory matching
 * against in-progress enrollment patterns before trial completion.
 *
 * <p>Each snapshot uses a week-numbered entity ID ({@code site-{id}-week-{N}}) so
 * different weeks accumulate while re-runs within the same week are idempotent.
 */
@ApplicationScoped
public class SiteEnrollmentTrajectoryJob {

    private static final Logger LOG = Logger.getLogger(SiteEnrollmentTrajectoryJob.class);

    private final SiteEnrollmentTrajectoryBuilder trajectoryBuilder;
    private final ClinicalCbrService cbrService;

    @ConfigProperty(name = "casehub.clinical.enrollment-trajectory.tenant-id", defaultValue = "default")
    String tenantId;

    @Inject
    public SiteEnrollmentTrajectoryJob(SiteEnrollmentTrajectoryBuilder trajectoryBuilder,
                                        ClinicalCbrService cbrService) {
        this.trajectoryBuilder = trajectoryBuilder;
        this.cbrService = cbrService;
    }

    @Scheduled(every = "${casehub.clinical.enrollment-trajectory.snapshot-interval:24h}",
               identity = "enrollment-trajectory-snapshot")
    public void snapshotAll() {
        int stored = 0;
        List<TrialSite> sites = QuarkusTransaction.requiringNew().call(() ->
            TrialSite.<TrialSite>list("tenantId", tenantId));

        for (TrialSite site : sites) {
            try {
                SiteContext ctx = QuarkusTransaction.requiringNew().call(() -> {
                    Instant earliest = PatientEnrollment.<PatientEnrollment>find(
                        "siteId = ?1 AND tenantId = ?2 AND enrolledAt IS NOT NULL ORDER BY enrolledAt ASC",
                        site.id, tenantId)
                        .firstResultOptional().map(e -> e.enrolledAt).orElse(null);
                    if (earliest == null) return null;

                    ClinicalTrial trial = ClinicalTrial.findById(site.trialId);
                    String phase = trial != null && trial.phase != null ? trial.phase.name() : "UNKNOWN";
                    return new SiteContext(earliest, site.targetEnrollment, phase);
                });
                if (ctx == null) continue;

                snapshotSite(site.id, site.trialId, ctx.earliest, ctx.targetEnrollment, ctx.trialPhase, tenantId);
                stored++;
            } catch (Exception e) {
                LOG.warnf(e, "Enrollment trajectory snapshot failed for site %s — skipping", site.id);
            }
        }
        if (stored > 0) {
            LOG.infof("Stored enrollment trajectory snapshots for %d sites", stored);
        }
    }

    void snapshotSite(UUID siteId, UUID trialId, Instant trialStart,
                       int targetEnrollment, String trialPhase, String tenantId) {
        List<Map<String, FeatureValue>> trajectory =
            trajectoryBuilder.buildTrajectory(siteId, trialId, trialStart, tenantId);
        if (trajectory.isEmpty()) return;

        long weeksSinceStart = Duration.between(trialStart, Instant.now()).toDays() / 7;
        int currentCount = trajectory.stream()
            .map(obs -> obs.get("cumulativeCount"))
            .filter(v -> v instanceof FeatureValue.NumberVal)
            .mapToInt(v -> (int) ((FeatureValue.NumberVal) v).value())
            .max().orElse(0);
        double progress = targetEnrollment > 0 ? (double) currentCount / targetEnrollment : 0.0;

        Map<String, Object> rawFeatures = new LinkedHashMap<>();
        rawFeatures.put("trialPhase", trialPhase);
        rawFeatures.put("targetEnrollment", targetEnrollment);
        rawFeatures.put("currentEnrollment", currentCount);
        rawFeatures.put("enrollmentProgress", progress);
        rawFeatures.put("weeksSinceTrialStart", weeksSinceStart);
        rawFeatures.put("enrollmentTrajectory", trajectory);

        Map<String, FeatureValue> features = FeatureValue.toFeatureMap(rawFeatures);

        String entityId = "site-" + siteId + "-week-" + weeksSinceStart;
        PlanCbrCase cbrCase = new PlanCbrCase(
            "Site enrollment at week %d of %s trial, target=%d, enrolled=%d (%.1f%%)".formatted(
                weeksSinceStart, trialPhase, targetEnrollment, currentCount, progress * 100),
            "Enrollment trajectory snapshot — periodic recording",
            "IN_PROGRESS", 1.0, features, List.of(),
            null, null);

        cbrService.storeIdempotent(cbrCase, "clinical-site-enrollment", entityId,
            ClinicalCbrDomains.SITE_ENROLLMENT, tenantId, null);
    }

    private record SiteContext(Instant earliest, int targetEnrollment, String trialPhase) {}
}
