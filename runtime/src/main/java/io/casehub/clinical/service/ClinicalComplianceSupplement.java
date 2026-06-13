package io.casehub.clinical.service;

import io.casehub.ledger.runtime.model.supplement.ComplianceSupplement;

/**
 * Factory for {@link ComplianceSupplement} instances covering each type of AI agent
 * decision in the clinical trial harness.
 *
 * Each supplement records the regulatory reference (planRef) and the algorithm or
 * policy component that made the decision (algorithmRef). All clinical AI decisions
 * are subject to human override — {@code humanOverrideAvailable = true} on every entry.
 *
 * Used by LedgerWriter beans to satisfy EU AI Act Art.12 and 21 CFR Part 312 audit
 * requirements for traceable, contestable AI decisions.
 */
public final class ClinicalComplianceSupplement {

    private ClinicalComplianceSupplement() {}

    public static ComplianceSupplement aeEscalation() {
        ComplianceSupplement s = new ComplianceSupplement();
        s.planRef = "ICH E6(R3) §5.17 — serious adverse event reporting";
        s.algorithmRef = "AdverseEventEscalationPolicy (rule-based CTCAE routing)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement irbDecision() {
        ComplianceSupplement s = new ComplianceSupplement();
        s.planRef = "21 CFR Part 312 — IRB review and approval";
        s.algorithmRef = "IrbCommitteePolicySpi (configurable IRB routing)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement protocolDeviation() {
        ComplianceSupplement s = new ComplianceSupplement();
        s.planRef = "ICH E6(R3) §4.5 — protocol deviation recording";
        s.algorithmRef = "ProtocolDeviationService (rule-based severity classification)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement susarGateDecision() {
        ComplianceSupplement s = new ComplianceSupplement();
        s.planRef = "ICH E2A §I.A.1 + 21 CFR 312.32 — SUSAR criteria and expedited reporting";
        s.algorithmRef = "SusarCriteriaEvaluator (rule-based CTCAE Grade 4/5 unexpected/suspected)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement safetyOfficerNotification() {
        ComplianceSupplement s = new ComplianceSupplement();
        s.planRef = "ICH E6(R3) §5.17 — safety officer notification on Grade 3+ AE";
        s.algorithmRef = "AdverseEventEscalationPolicy (rule-based CTCAE routing)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement sponsorNotification() {
        ComplianceSupplement s = new ComplianceSupplement();
        s.planRef = "ICH E6(R3) §5.17 — sponsor notification on serious adverse event";
        s.algorithmRef = "AdverseEventEscalationPolicy (rule-based CTCAE routing)";
        s.humanOverrideAvailable = true;
        return s;
    }
}
