package io.casehub.clinical.service;

import io.casehub.api.engine.YamlCaseHub;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Case definition for IND expedited safety report filing (21 CFR 312.32(c)(1)(i)).
 *
 * <p>Started when Grade 5 + unexpected AE is reported — concurrently with the AE
 * escalation case and SUSAR oversight case. Routes to the regulatory-submission
 * capability via trust-weighted routing. In Phase 0 (bootstrap, no trust data)
 * falls back to availability routing.
 *
 * <p>No Java function worker — the regulatory-submission capability is fulfilled
 * by an external agent or human task, not a local Java function.
 */
@ApplicationScoped
public class ClinicalRegulatorySubmissionCaseHub extends YamlCaseHub {

    public ClinicalRegulatorySubmissionCaseHub() {
        super("clinical/regulatory-submission.yaml");
    }
}
