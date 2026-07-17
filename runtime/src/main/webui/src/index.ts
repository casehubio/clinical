import { loadSite } from "@casehubio/pages-runtime";
import { onPagesEvent } from "@casehubio/pages-component";
import "@casehubio/blocks-ui-work-item-inbox";
import { app } from "./app.js";
import { DEMO_MODE } from "./datasets.js";
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

const CLINICAL_IDENTITY = {
  userId: "demo-coordinator",
  displayName: "Demo Coordinator",
  groups: ["SPONSOR", "INVESTIGATOR", "COORDINATOR", "MONITOR"],
};

const DEMO_WORK_ITEMS = [
  { item: { id: "wi-001", title: "Review SUSAR ae-003", description: null, types: ["adverse-event"], category: "adverse-event", formKey: null, status: "claimed", priority: "high", assigneeId: "demo-coordinator", owner: null, candidateGroups: "clinical", candidateUsers: null, requiredCapabilities: null, createdBy: null, delegationDeclineTarget: null, delegationChain: null, priorStatus: null, payload: '{"aeId":"ae-003"}', resolution: null, claimDeadline: null, expiresAt: null, followUpDate: null, createdAt: "2026-07-15T10:00:00Z", updatedAt: "2026-07-15T10:00:00Z", assignedAt: null, startedAt: null, completedAt: null, suspendedAt: null, labels: [], confidenceScore: null, callerRef: null, version: 1, templateId: null, outcome: null, permittedOutcomes: null, inputDataSchema: null, outputDataSchema: null, excludedUsers: null, scope: null, percentComplete: null, statusNote: null }, childCount: 0, completedCount: null, requiredCount: null, groupStatus: null },
  { item: { id: "wi-002", title: "Approve deviation dev-002", description: null, types: ["deviation-review"], category: "deviation-review", formKey: null, status: "pending", priority: "medium", assigneeId: null, owner: null, candidateGroups: "clinical", candidateUsers: null, requiredCapabilities: null, createdBy: null, delegationDeclineTarget: null, delegationChain: null, priorStatus: null, payload: '{"deviationId":"dev-002"}', resolution: null, claimDeadline: null, expiresAt: null, followUpDate: null, createdAt: "2026-07-15T11:00:00Z", updatedAt: "2026-07-15T11:00:00Z", assignedAt: null, startedAt: null, completedAt: null, suspendedAt: null, labels: [], confidenceScore: null, callerRef: null, version: 1, templateId: null, outcome: null, permittedOutcomes: null, inputDataSchema: null, outputDataSchema: null, excludedUsers: null, scope: null, percentComplete: null, statusNote: null }, childCount: 0, completedCount: null, requiredCount: null, groupStatus: null },
  { item: { id: "wi-003", title: "IRB review for dev-004", description: null, types: ["deviation-review"], category: "deviation-review", formKey: null, status: "pending", priority: "high", assigneeId: null, owner: null, candidateGroups: "clinical", candidateUsers: null, requiredCapabilities: null, createdBy: null, delegationDeclineTarget: null, delegationChain: null, priorStatus: null, payload: '{"deviationId":"dev-004"}', resolution: null, claimDeadline: null, expiresAt: null, followUpDate: null, createdAt: "2026-07-15T12:00:00Z", updatedAt: "2026-07-15T12:00:00Z", assignedAt: null, startedAt: null, completedAt: null, suspendedAt: null, labels: [], confidenceScore: null, callerRef: null, version: 1, templateId: null, outcome: null, permittedOutcomes: null, inputDataSchema: null, outputDataSchema: null, excludedUsers: null, scope: null, percentComplete: null, statusNote: null }, childCount: 0, completedCount: null, requiredCount: null, groupStatus: null },
  { item: { id: "wi-004", title: "IND filing deadline ae-001", description: null, types: ["adverse-event"], category: "adverse-event", formKey: null, status: "claimed", priority: "urgent", assigneeId: "demo-coordinator", owner: null, candidateGroups: "clinical", candidateUsers: null, requiredCapabilities: null, createdBy: null, delegationDeclineTarget: null, delegationChain: null, priorStatus: null, payload: '{"aeId":"ae-001"}', resolution: null, claimDeadline: null, expiresAt: null, followUpDate: null, createdAt: "2026-07-14T08:00:00Z", updatedAt: "2026-07-14T08:00:00Z", assignedAt: null, startedAt: null, completedAt: null, suspendedAt: null, labels: [], confidenceScore: null, callerRef: null, version: 1, templateId: null, outcome: null, permittedOutcomes: null, inputDataSchema: null, outputDataSchema: null, excludedUsers: null, scope: null, percentComplete: null, statusNote: null }, childCount: 0, completedCount: null, requiredCount: null, groupStatus: null },
  { item: { id: "wi-005", title: "PI authorisation dev-005", description: null, types: ["deviation-review"], category: "deviation-review", formKey: null, status: "pending", priority: "medium", assigneeId: null, owner: null, candidateGroups: "clinical", candidateUsers: null, requiredCapabilities: null, createdBy: null, delegationDeclineTarget: null, delegationChain: null, priorStatus: null, payload: '{"deviationId":"dev-005"}', resolution: null, claimDeadline: null, expiresAt: null, followUpDate: null, createdAt: "2026-07-15T14:00:00Z", updatedAt: "2026-07-15T14:00:00Z", assignedAt: null, startedAt: null, completedAt: null, suspendedAt: null, labels: [], confidenceScore: null, callerRef: null, version: 1, templateId: null, outcome: null, permittedOutcomes: null, inputDataSchema: null, outputDataSchema: null, excludedUsers: null, scope: null, percentComplete: null, statusNote: null }, childCount: 0, completedCount: null, requiredCount: null, groupStatus: null },
];

function configureWorkItemInbox() {
  const inbox = document.querySelector("work-item-inbox");
  if (!inbox) return;
  (inbox as any).identity = CLINICAL_IDENTITY;
  if (DEMO_MODE) {
    (inbox as any).data = DEMO_WORK_ITEMS;
  }

  onPagesEvent<{ workItemId: string }>(document, "work-item:selected", (payload) => {
    const items = (inbox as any).data ?? (inbox as any).items ?? [];
    const match = items.find((r: any) => r.item?.id === payload.workItemId);
    if (!match) return;
    const types: string[] = match.item.types ?? [];
    if (types.includes("adverse-event")) {
      window.location.hash = "#/page/Safety%20Workbench";
    } else if (types.includes("deviation-review")) {
      window.location.hash = "#/page/Protocol%20Workbench";
    }
  });
}

const container = document.getElementById("app");
if (container) {
  loadSite(container, app).then(() => {
    configureWorkItemInbox();
  }).catch((err) => {
    console.error("loadSite failed:", err);
    container.innerHTML = `<pre style="color:red;padding:2rem;">${err?.stack ?? err}</pre>`;
  });
}
