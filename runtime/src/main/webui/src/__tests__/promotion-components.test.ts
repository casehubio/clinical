import { describe, it, expect, beforeAll } from "vitest";
import { ClinicalCbrPrecedentsPanel } from "../components/cbr-precedents-panel.js";
import { ClinicalTrustFeedbackDisplay } from "../components/trust-feedback-display.js";
import { ClinicalRegulatoryComplianceSummary } from "../components/regulatory-compliance-summary.js";
import { ClinicalGdprErasureAction } from "../components/gdpr-erasure-action.js";
import { ClinicalSlaBreachPolicyIndicator } from "../components/sla-breach-policy-indicator.js";

describe("promotion components", () => {
  beforeAll(() => {
    const defs: [string, CustomElementConstructor][] = [
      ["cbr-precedents-panel", ClinicalCbrPrecedentsPanel],
      ["trust-feedback-display", ClinicalTrustFeedbackDisplay],
      ["regulatory-compliance-summary", ClinicalRegulatoryComplianceSummary],
      ["gdpr-erasure-action", ClinicalGdprErasureAction],
      ["sla-breach-policy-indicator", ClinicalSlaBreachPolicyIndicator],
    ];
    for (const [name, ctor] of defs) {
      if (!customElements.get(name)) customElements.define(name, ctor);
    }
  });

  it("cbr-precedents-panel renders empty state", async () => {
    const el = document.createElement("cbr-precedents-panel") as ClinicalCbrPrecedentsPanel;
    document.body.appendChild(el);
    await el.updateComplete;
    expect(el.shadowRoot?.textContent).toContain("No similar cases found");
    el.remove();
  });

  it("trust-feedback-display renders decision card", async () => {
    const el = document.createElement("trust-feedback-display") as ClinicalTrustFeedbackDisplay;
    el.gateDecision = {
      decision: "APPROVED",
      investigator: "Dr. Smith",
      attestation: "ENDORSED",
      trustScoreBefore: 0.75,
      trustScoreAfter: 0.82,
      dimension: "safety-accuracy",
    };
    document.body.appendChild(el);
    await el.updateComplete;
    const text = el.shadowRoot?.textContent ?? "";
    expect(text).toContain("APPROVED");
    expect(text).toContain("Dr. Smith");
    expect(text).toContain("0.75");
    expect(text).toContain("0.82");
    el.remove();
  });

  it("trust-feedback-display compact mode renders single line", async () => {
    const el = document.createElement("trust-feedback-display") as ClinicalTrustFeedbackDisplay;
    el.compact = true;
    el.gateDecision = {
      decision: "APPROVED",
      investigator: "Dr. Smith",
      attestation: "ENDORSED",
      trustScoreBefore: 0.75,
      trustScoreAfter: 0.82,
      dimension: "safety-accuracy",
    };
    document.body.appendChild(el);
    await el.updateComplete;
    const children = el.shadowRoot?.querySelectorAll(".compact") ?? [];
    expect(children.length).toBeGreaterThan(0);
    el.remove();
  });

  it("regulatory-compliance-summary renders requirements", async () => {
    const el = document.createElement("regulatory-compliance-summary") as ClinicalRegulatoryComplianceSummary;
    el.requirements = [
      { regulation: "FDA 21 CFR 312.32", requirement: "Expedited safety reporting", mechanism: "SLA WorkItem", status: "MET" },
      { regulation: "GDPR Art.17", requirement: "Right to erasure", mechanism: "LedgerErasureService", status: "MET" },
    ];
    document.body.appendChild(el);
    await el.updateComplete;
    const text = el.shadowRoot?.textContent ?? "";
    expect(text).toContain("FDA 21 CFR 312.32");
    expect(text).toContain("GDPR Art.17");
    el.remove();
  });

  it("gdpr-erasure-action renders input form", async () => {
    const el = document.createElement("gdpr-erasure-action") as ClinicalGdprErasureAction;
    el.subjectLabel = "Patient";
    document.body.appendChild(el);
    await el.updateComplete;
    const text = el.shadowRoot?.textContent ?? "";
    expect(text).toContain("Patient");
    expect(el.shadowRoot?.querySelector("input")).toBeTruthy();
    el.remove();
  });

  it("sla-breach-policy-indicator renders tiers", async () => {
    const el = document.createElement("sla-breach-policy-indicator") as ClinicalSlaBreachPolicyIndicator;
    el.tiers = [
      { threshold: 0.75, label: "Warning", consequence: "Sponsor notified", regulation: "ICH E6(R3)" },
      { threshold: 1.0, label: "Breach", consequence: "Regulatory filing required", regulation: "21 CFR 312.32" },
    ];
    document.body.appendChild(el);
    await el.updateComplete;
    const text = el.shadowRoot?.textContent ?? "";
    expect(text).toContain("Warning");
    expect(text).toContain("Breach");
    expect(text).toContain("21 CFR 312.32");
    el.remove();
  });
});
