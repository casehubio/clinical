package io.casehub.clinical.service;

import io.casehub.api.engine.YamlCaseHub;
import jakarta.enterprise.context.ApplicationScoped;

/** Case definition for eligibility screening IRB consultation gate (Layer 9). */
@ApplicationScoped
public class EligibilityScreeningCaseHub extends YamlCaseHub {
    public EligibilityScreeningCaseHub() { super("clinical/eligibility-screening.yaml"); }
}
