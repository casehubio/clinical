package io.casehub.clinical.service.api;

import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.spi.ClinicalDeviationApi;
import io.casehub.clinical.api.view.DeviationView;
import io.casehub.clinical.api.view.ReportDeviationRequest;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.service.ProtocolDeviationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.UUID;

@ApplicationScoped
public class DefaultClinicalDeviationApi implements ClinicalDeviationApi {

    @Inject ProtocolDeviationService deviationService;
    @Inject
            jakarta.persistence.EntityManager em;


    @Override
    @Transactional
    public DeviationView reportDeviation(UUID trialId, UUID siteId,
                                          ReportDeviationRequest req, String tenancyId) {
        TrialSite site = em.createNamedQuery("TrialSite.findByIdAndTenantId", TrialSite.class).setParameter("id", siteId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (site == null || !site.trialId.equals(trialId))
            throw new NotFoundException("Site not found");

        ProtocolDeviation deviation = new ProtocolDeviation();
        deviation.id = UUID.randomUUID();
        deviation.tenantId = site.tenantId;
        deviation.siteId = siteId;
        deviation.deviationType = req.deviationType();
        deviation.severity = req.severity();
        deviation.piApprovalStatus = PiApprovalStatus.PENDING;

        deviationService.reportDeviation(deviation);
        return toView(deviation);
    }

    @Override
    public DeviationView getDeviation(UUID trialId, UUID siteId, UUID deviationId, String tenancyId) {
        ProtocolDeviation dev = em.createNamedQuery("ProtocolDeviation.findByIdAndTenantId", ProtocolDeviation.class).setParameter("id", deviationId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (dev == null || !dev.siteId.equals(siteId)) return null;
        TrialSite site = em.createNamedQuery("TrialSite.findByIdAndTenantId", TrialSite.class).setParameter("id", siteId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (site == null || !site.trialId.equals(trialId)) return null;
        return toView(dev);
    }

    private static DeviationView toView(ProtocolDeviation d) {
        return new DeviationView(d.id, d.siteId, d.deviationType, d.severity,
                                  d.piApprovalStatus, d.commandedAt, d.responseDeadline,
                                  d.escalationRequirement);
    }
}
