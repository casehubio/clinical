package io.casehub.clinical.service;

import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

/** Resolves the engine case ID for the trial that owns a given site. */
@ApplicationScoped
public class TrialCaseLookup {

    @Transactional(Transactional.TxType.SUPPORTS)
    public UUID findTrialEngineCase(UUID siteId) {
        TrialSite site = TrialSite.findById(siteId);
        if (site == null) return null;
        ClinicalTrial trial = ClinicalTrial.findById(site.trialId);
        return trial != null ? trial.engineCaseId : null;
    }
}
