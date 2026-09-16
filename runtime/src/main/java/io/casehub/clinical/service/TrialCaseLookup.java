package io.casehub.clinical.service;

import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.UUID;

/** Resolves the engine case ID for the trial that owns a given site. */
@ApplicationScoped
public class TrialCaseLookup {
    @Inject
    EntityManager em;


    @Transactional(Transactional.TxType.SUPPORTS)
    public UUID findTrialEngineCase(UUID siteId) {
        TrialSite site = em.find(TrialSite.class, siteId);
        if (site == null) return null;
        ClinicalTrial trial = em.find(ClinicalTrial.class, site.trialId);
        return trial != null ? trial.engineCaseId : null;
    }
}
