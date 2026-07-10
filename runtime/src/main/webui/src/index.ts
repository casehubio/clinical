import { loadSite } from "@casehubio/pages-runtime";
import { app } from "./app.js";
import { ClinicalCommitmentLifecycle } from "./components/commitment-lifecycle.js";
import { ClinicalCbrPrecedentsPanel } from "./components/cbr-precedents-panel.js";
import { ClinicalTrustFeedbackDisplay } from "./components/trust-feedback-display.js";
import { ClinicalRegulatoryComplianceSummary } from "./components/regulatory-compliance-summary.js";
import { ClinicalGdprErasureAction } from "./components/gdpr-erasure-action.js";
import { ClinicalSlaBreachPolicyIndicator } from "./components/sla-breach-policy-indicator.js";

const components: [string, CustomElementConstructor][] = [
  ["commitment-lifecycle", ClinicalCommitmentLifecycle],
  ["cbr-precedents-panel", ClinicalCbrPrecedentsPanel],
  ["trust-feedback-display", ClinicalTrustFeedbackDisplay],
  ["regulatory-compliance-summary", ClinicalRegulatoryComplianceSummary],
  ["gdpr-erasure-action", ClinicalGdprErasureAction],
  ["sla-breach-policy-indicator", ClinicalSlaBreachPolicyIndicator],
];

for (const [name, ctor] of components) {
  if (!customElements.get(name)) customElements.define(name, ctor);
}

const container = document.getElementById("app");
if (container) {
  loadSite(container, app).catch((err) => {
    console.error("loadSite failed:", err);
    container.innerHTML = `<pre style="color:red;padding:2rem;">${err?.stack ?? err}</pre>`;
  });
}
