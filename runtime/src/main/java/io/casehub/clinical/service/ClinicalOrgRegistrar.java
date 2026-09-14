package io.casehub.clinical.service;

import io.casehub.eidos.org.api.OrgRegistry;
import io.casehub.eidos.org.api.OrgStructure;

public final class ClinicalOrgRegistrar {

    private ClinicalOrgRegistrar() {}

    public static void register(OrgRegistry registry, String tenancyId) {
        OrgStructure.define(tenancyId)
                .unit("clinical-ops").name("Clinical Trial Operations").kind("department").add()
                .unit("safety-team").name("Safety Team").kind("functional")
                    .parentUnit("clinical-ops")
                    .capability("safety-monitoring").capability("susar-criteria").add()
                .unit("site-team").name("Site Team").kind("functional")
                    .parentUnit("clinical-ops")
                    .capability("eligibility-screening").add()
                .unit("regulatory-team").name("Regulatory Team").kind("functional")
                    .parentUnit("clinical-ops")
                    .capability("regulatory-submission").capability("protocol-amendment-advisor").add()
                .supervises("trial-supervisor", "safety-team").scope("safety-monitoring").add()
                .supervises("trial-supervisor", "site-team").scope("eligibility-screening").add()
                .supervises("trial-supervisor", "regulatory-team").scope("regulatory-submission").add()
                .escalatesTo("site-team", "safety-team").add()
                .escalatesTo("safety-team", "regulatory-team").add()
                .build()
                .registerAll(registry);
    }
}
