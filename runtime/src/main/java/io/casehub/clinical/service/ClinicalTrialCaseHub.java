package io.casehub.clinical.service;

import io.casehub.api.engine.YamlCaseHub;
import jakarta.enterprise.context.ApplicationScoped;

/** Case definition for trial-level cross-site safety coordination (Layer 6). */
@ApplicationScoped
public class ClinicalTrialCaseHub extends YamlCaseHub {

    public ClinicalTrialCaseHub() {
        super("clinical/trial-coordination.yaml");
    }
}
