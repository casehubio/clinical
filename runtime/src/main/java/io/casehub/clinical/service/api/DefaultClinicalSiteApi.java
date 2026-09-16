package io.casehub.clinical.service.api;

import io.casehub.clinical.api.model.SiteStatus;
import io.casehub.clinical.api.spi.ClinicalSiteApi;
import io.casehub.clinical.api.view.AddSiteRequest;
import io.casehub.clinical.api.view.SiteView;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.UUID;

@ApplicationScoped
public class DefaultClinicalSiteApi implements ClinicalSiteApi {
    @jakarta.inject.Inject
    jakarta.persistence.EntityManager em;


    @Override
    @Transactional
    public SiteView addSite(UUID trialId, AddSiteRequest req, String tenancyId) {
        ClinicalTrial trial = em.createNamedQuery("ClinicalTrial.findByIdAndTenantId", ClinicalTrial.class).setParameter("id", trialId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (trial == null) throw new NotFoundException("Trial not found: " + trialId);

        TrialSite site = new TrialSite();
        site.id = UUID.randomUUID();
        site.tenantId = trial.tenantId;
        site.trialId = trialId;
        site.investigatorId = req.investigatorId();
        site.targetEnrollment = req.targetEnrollment();
        site.status = SiteStatus.PENDING;
        em.persist(site);
        return toView(site);
    }

    @Override
    public SiteView getSite(UUID trialId, UUID siteId, String tenancyId) {
        TrialSite site = em.createNamedQuery("TrialSite.findByIdAndTenantId", TrialSite.class).setParameter("id", siteId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (site == null || !site.trialId.equals(trialId)) return null;
        return toView(site);
    }

    private static SiteView toView(TrialSite s) {
        return new SiteView(s.id, s.trialId, s.investigatorId,
                            s.targetEnrollment, s.status);
    }
}
