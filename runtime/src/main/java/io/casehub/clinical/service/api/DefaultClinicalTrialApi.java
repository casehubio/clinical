package io.casehub.clinical.service.api;

import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.api.spi.ClinicalTrialApi;
import io.casehub.clinical.api.view.RegisterTrialRequest;
import io.casehub.clinical.api.view.SponsorConfigRequest;
import io.casehub.clinical.api.view.TrialListView;
import io.casehub.clinical.api.view.TrialView;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TenantEntityLookup;
import io.casehub.clinical.service.TrialActivationService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DefaultClinicalTrialApi implements ClinicalTrialApi {

    @Inject TrialActivationService trialActivationService;
    @Inject io.casehub.platform.api.identity.CurrentPrincipal currentPrincipal;
    @Inject
            jakarta.persistence.EntityManager em;


    @Override
    public List<TrialListView> listTrials(String tenancyId) {
        List<ClinicalTrial> trials = em.createQuery("SELECT t FROM ClinicalTrial t WHERE t.tenantId = :tenantId", ClinicalTrial.class).setParameter("tenantId", tenancyId).getResultList();
        return trials.stream().map(t -> new TrialListView(
                t.id, t.protocolId, t.phase, t.sponsor, t.status, t.targetEnrollment
        )).toList();
    }

    @Override
    @Transactional
    public TrialView registerTrial(RegisterTrialRequest req, String tenancyId) {
        if (req.protocolId() == null || req.protocolId().isBlank()) throw new BadRequestException("protocolId is required");
        if (req.phase() == null) throw new BadRequestException("phase is required");
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = UUID.randomUUID();
        trial.protocolId = req.protocolId();
        trial.phase = req.phase();
        trial.sponsor = req.sponsor();
        trial.targetEnrollment = req.targetEnrollment();
        trial.status = TrialStatus.PLANNING;
        trial.sponsorNotificationConnectorId = req.sponsorNotificationConnectorId();
        trial.sponsorNotificationDestination = req.sponsorNotificationDestination();
        trial.tenantId = tenancyId;
        em.persist(trial);
        return toView(trial);
    }

    @Override
    public TrialView getTrial(UUID id, String tenancyId) {
        ClinicalTrial trial;
        if (currentPrincipal.isCrossTenantAdmin()) {
            trial = em.find(ClinicalTrial.class, id);
        } else {
            trial = em.createNamedQuery("ClinicalTrial.findByIdAndTenantId", ClinicalTrial.class).setParameter("id", id).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        }
        if (trial == null) return null;
        return toView(trial);
    }

    @Override
    @Transactional
    public void updateSponsorConfig(UUID id, SponsorConfigRequest req, String tenancyId) {
        if (req == null) throw new BadRequestException("Request body is required");
        if (req.connectorId() != null && req.connectorId().length() > 64) throw new BadRequestException("connectorId must be 64 characters or fewer");
        if (req.destination() != null && req.destination().length() > 2048) throw new BadRequestException("destination must be 2048 characters or fewer");
        ClinicalTrial trial = em.createNamedQuery("ClinicalTrial.findByIdAndTenantId", ClinicalTrial.class).setParameter("id", id).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (trial == null) throw new NotFoundException("Trial not found: " + id);
        trial.sponsorNotificationConnectorId = req.connectorId();
        trial.sponsorNotificationDestination = req.destination();
    }

    @Override
    public void activateTrial(UUID id, String tenancyId) {
        try {
            trialActivationService.activate(id);
        } catch (TrialActivationService.TrialNotFoundException e) {
            throw new NotFoundException(e.getMessage(), e);
        } catch (TrialActivationService.TrialNotInPlanningStatusException e) {
            throw new WebApplicationException(e.getMessage(), 409);
        }
    }

    private static TrialView toView(ClinicalTrial t) {
        return new TrialView(t.id, t.protocolId, t.phase, t.sponsor,
                             t.targetEnrollment, t.status,
                             t.sponsorNotificationConnectorId,
                             t.sponsorNotificationDestination);
    }
}
