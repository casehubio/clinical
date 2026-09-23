package io.casehub.clinical.service.api;

import io.casehub.clinical.api.model.AmendmentPrecedentResponse;
import io.casehub.clinical.api.model.AmendmentPrecedentSearchResponse;
import io.casehub.clinical.api.spi.ClinicalAmendmentApi;
import io.casehub.clinical.api.view.AmendmentView;
import io.casehub.clinical.api.view.ProposeAmendmentRequest;
import io.casehub.clinical.cbr.ClinicalCbrDomains;
import io.casehub.clinical.cbr.ClinicalCbrService;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.clinical.service.ProtocolAmendmentService;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class DefaultClinicalAmendmentApi implements ClinicalAmendmentApi {

    @Inject ProtocolAmendmentService service;
    @Inject ClinicalCbrService cbrService;
    @Inject
            EntityManager      em;


    @Override
    public List<AmendmentView> listAmendments(UUID trialId, String tenancyId) {
        ClinicalTrial trial = em.createNamedQuery("ClinicalTrial.findByIdAndTenantId", ClinicalTrial.class).setParameter("id", trialId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (trial == null) return List.of();
        List<ProtocolAmendment> amendments = em.createQuery("SELECT a FROM ProtocolAmendment a WHERE a.trialId = :trialId AND a.tenantId = :tenantId", ProtocolAmendment.class).setParameter("trialId", trialId).setParameter("tenantId", trial.tenantId).getResultList();
        return amendments.stream().map(DefaultClinicalAmendmentApi::toView).toList();
    }

    @Override
    public AmendmentView proposeAmendment(UUID trialId, ProposeAmendmentRequest req, String tenancyId) {
        ClinicalTrial trial = em.createNamedQuery("ClinicalTrial.findByIdAndTenantId", ClinicalTrial.class).setParameter("id", trialId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (trial == null) throw new NotFoundException("Trial not found: " + trialId);
        ProtocolAmendment amendment = service.propose(trialId, req.proposedChange(), tenancyId);
        return toView(amendment);
    }

    @Override
    public AmendmentView getAmendment(UUID trialId, UUID amendmentId, String tenancyId) {
        ProtocolAmendment amendment = em.createNamedQuery("ProtocolAmendment.findByIdAndTenantId", ProtocolAmendment.class).setParameter("id", amendmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (amendment == null || !amendment.trialId.equals(trialId)) return null;
        return toView(amendment);
    }

    @Override
    public AmendmentPrecedentSearchResponse getAmendmentPrecedents(UUID trialId, UUID amendmentId,
                                                                     String tenancyId, String actorId) {
        ProtocolAmendment amendment = em.createNamedQuery("ProtocolAmendment.findByIdAndTenantId", ProtocolAmendment.class).setParameter("id", amendmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (amendment == null || !amendment.trialId.equals(trialId))
            throw new NotFoundException("Amendment not found");

        io.casehub.platform.api.path.Path queryScope = io.casehub.platform.api.path.Path.of(trialId.toString());
        CbrQuery query = CbrQuery.of(tenancyId, ClinicalCbrDomains.AMENDMENT, queryScope,
                                      "clinical-amendment", Map.of(), 10).withVectorWeight(0.0);

        var result = cbrService.retrieveWithAudit(query, CbrFeatureRecord.class, amendmentId, actorId);
        List<AmendmentPrecedentResponse> precedents = result.cases().stream()
                .map(this::mapToAmendmentResponse).toList();
        return new AmendmentPrecedentSearchResponse(result.traceId(), result.explanation(), precedents);
    }

    private AmendmentPrecedentResponse mapToAmendmentResponse(CbrMatch<CbrFeatureRecord> scored) {
        CbrFeatureRecord c = scored.cbrRecord();
        return new AmendmentPrecedentResponse(scored.score(), c.problem(), c.solution(), c.outcome());
    }

    private static AmendmentView toView(ProtocolAmendment a) {
        return new AmendmentView(a.id.toString(), a.trialId.toString(), a.proposedChange,
                                  a.status.name(), a.amendmentCaseStatus.name(), a.proposedAt.toString());
    }
}
