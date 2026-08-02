package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrRetentionPolicy;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Optional;

@ApplicationScoped
public class CbrRetentionPurgeJob {

    private static final Logger LOG = Logger.getLogger(CbrRetentionPurgeJob.class);
    private static final int LARGE_DELETE_THRESHOLD = 10000;

    private final CbrCaseMemoryStore store;

    @ConfigProperty(name = "casehub.clinical.cbr.retention.tenant-id", defaultValue = "default")
    String tenantId;

    @ConfigProperty(name = "casehub.clinical.cbr.retention.ae.max-age-days")
    Optional<Integer> aeMaxAgeDaysOpt;
    @ConfigProperty(name = "casehub.clinical.cbr.retention.ae.max-cases")
    Optional<Integer> aeMaxCasesOpt;
    @ConfigProperty(name = "casehub.clinical.cbr.retention.ae-trajectory.max-age-days")
    Optional<Integer> aeTrajectoryMaxAgeDaysOpt;
    @ConfigProperty(name = "casehub.clinical.cbr.retention.ae-trajectory.max-cases")
    Optional<Integer> aeTrajectoryMaxCasesOpt;
    @ConfigProperty(name = "casehub.clinical.cbr.retention.trial-safety.max-age-days")
    Optional<Integer> trialSafetyMaxAgeDaysOpt;
    @ConfigProperty(name = "casehub.clinical.cbr.retention.trial-safety.max-cases")
    Optional<Integer> trialSafetyMaxCasesOpt;
    @ConfigProperty(name = "casehub.clinical.cbr.retention.deviation.max-age-days")
    Optional<Integer> deviationMaxAgeDaysOpt;
    @ConfigProperty(name = "casehub.clinical.cbr.retention.deviation.max-cases")
    Optional<Integer> deviationMaxCasesOpt;
    @ConfigProperty(name = "casehub.clinical.cbr.retention.amendment.max-age-days")
    Optional<Integer> amendmentMaxAgeDaysOpt;
    @ConfigProperty(name = "casehub.clinical.cbr.retention.amendment.max-cases")
    Optional<Integer> amendmentMaxCasesOpt;
    @ConfigProperty(name = "casehub.clinical.cbr.retention.site-enrollment.max-age-days")
    Optional<Integer> siteEnrollmentMaxAgeDaysOpt;
    @ConfigProperty(name = "casehub.clinical.cbr.retention.site-enrollment.max-cases")
    Optional<Integer> siteEnrollmentMaxCasesOpt;

    // Test-visible fields — set by tests to bypass Optional<> config injection
    Integer aeMaxAgeDays;
    Integer aeMaxCases;
    Integer aeTrajectoryMaxAgeDays;
    Integer aeTrajectoryMaxCases;
    Integer trialSafetyMaxAgeDays;
    Integer trialSafetyMaxCases;
    Integer deviationMaxAgeDays;
    Integer deviationMaxCases;
    Integer amendmentMaxAgeDays;
    Integer amendmentMaxCases;
    Integer siteEnrollmentMaxAgeDays;
    Integer siteEnrollmentMaxCases;

    @Inject
    public CbrRetentionPurgeJob(CbrCaseMemoryStore store) {
        this.store = store;
    }

    @Scheduled(every = "${casehub.clinical.cbr.retention.interval:168h}",
               identity = "cbr-retention-purge")
    public void purgeAll() {
        purgeDomain(ClinicalCbrDomains.AE, resolveMaxAge(aeMaxAgeDays, aeMaxAgeDaysOpt), resolveMaxCases(aeMaxCases, aeMaxCasesOpt));
        purgeDomain(ClinicalCbrDomains.AE_TRAJECTORY, resolveMaxAge(aeTrajectoryMaxAgeDays, aeTrajectoryMaxAgeDaysOpt), resolveMaxCases(aeTrajectoryMaxCases, aeTrajectoryMaxCasesOpt));
        purgeDomain(ClinicalCbrDomains.TRIAL_SAFETY, resolveMaxAge(trialSafetyMaxAgeDays, trialSafetyMaxAgeDaysOpt), resolveMaxCases(trialSafetyMaxCases, trialSafetyMaxCasesOpt));
        purgeDomain(ClinicalCbrDomains.DEVIATION, resolveMaxAge(deviationMaxAgeDays, deviationMaxAgeDaysOpt), resolveMaxCases(deviationMaxCases, deviationMaxCasesOpt));
        purgeDomain(ClinicalCbrDomains.AMENDMENT, resolveMaxAge(amendmentMaxAgeDays, amendmentMaxAgeDaysOpt), resolveMaxCases(amendmentMaxCases, amendmentMaxCasesOpt));
        purgeDomain(ClinicalCbrDomains.SITE_ENROLLMENT, resolveMaxAge(siteEnrollmentMaxAgeDays, siteEnrollmentMaxAgeDaysOpt), resolveMaxCases(siteEnrollmentMaxCases, siteEnrollmentMaxCasesOpt));
    }

    private void purgeDomain(MemoryDomain domain, Integer maxAgeDays, Integer maxCases) {
        if (maxAgeDays == null && maxCases == null) return;

        try {
            var policy = new CbrRetentionPolicy(tenantId, domain, null, maxAgeDays, maxCases, null);
            int purged = store.purge(policy);

            if (purged > LARGE_DELETE_THRESHOLD) {
                LOG.warnf("CBR retention purge deleted %d cases from %s — unusually large", purged, domain.name());
            } else if (purged > 0) {
                LOG.infof("CBR retention purge: removed %d cases from %s", purged, domain.name());
            }
        } catch (Exception e) {
            LOG.errorf(e, "CBR retention purge failed for domain %s — skipping", domain.name());
        }
    }

    private static Integer resolveMaxAge(Integer testField, Optional<Integer> configOpt) {
        if (testField != null) return testField;
        return configOpt != null ? configOpt.orElse(null) : null;
    }

    private static Integer resolveMaxCases(Integer testField, Optional<Integer> configOpt) {
        if (testField != null) return testField;
        return configOpt != null ? configOpt.orElse(null) : null;
    }
}
