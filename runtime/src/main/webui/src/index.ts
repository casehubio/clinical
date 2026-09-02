import { loadSite } from "@casehubio/pages-runtime";
import { onPagesEvent } from "@casehubio/pages-component";
import "@casehubio/blocks-ui-work-item-inbox";
import { app } from "./app.js";
import { ClinicalCommitmentLifecycle } from "./components/commitment-lifecycle.js";
import { ClinicalCbrPrecedentsPanel } from "./components/cbr-precedents-panel.js";
import { ClinicalTrustFeedbackDisplay } from "./components/trust-feedback-display.js";
import { ClinicalRegulatoryComplianceSummary } from "./components/regulatory-compliance-summary.js";
import { ClinicalGdprErasureAction } from "./components/gdpr-erasure-action.js";
import { ClinicalSlaBreachPolicyIndicator } from "./components/sla-breach-policy-indicator.js";
import { ClinicalAeGradeHistory } from "./components/ae-grade-history.js";
import { ClinicalAeRegrade } from "./components/ae-regrade.js";
import { ClinicalAuditTrail } from "./components/clinical-audit-trail.js";

const components: [string, CustomElementConstructor][] = [
  ["commitment-lifecycle", ClinicalCommitmentLifecycle],
  ["cbr-precedents-panel", ClinicalCbrPrecedentsPanel],
  ["trust-feedback-display", ClinicalTrustFeedbackDisplay],
  ["regulatory-compliance-summary", ClinicalRegulatoryComplianceSummary],
  ["gdpr-erasure-action", ClinicalGdprErasureAction],
  ["sla-breach-policy-indicator", ClinicalSlaBreachPolicyIndicator],
  ["clinical-ae-grade-history", ClinicalAeGradeHistory],
  ["clinical-ae-regrade", ClinicalAeRegrade],
  ["clinical-audit-trail", ClinicalAuditTrail],
];

for (const [name, ctor] of components) {
  if (!customElements.get(name)) customElements.define(name, ctor);
}

const CLINICAL_IDENTITY = {
  userId: "demo-coordinator",
  displayName: "Demo Coordinator",
  groups: ["SPONSOR", "INVESTIGATOR", "COORDINATOR", "MONITOR"],
};

function configureWorkItemInbox() {
  const inbox = document.querySelector("work-item-inbox");
  if (!inbox) return;
  (inbox as any).identity = CLINICAL_IDENTITY;

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

const DEFAULT_TRIAL_ID = "316e3846-4ea7-3b18-a6f7-e01ce6582a69";
const trialId = new URLSearchParams(window.location.search).get("trialId") || DEFAULT_TRIAL_ID;

function configureWorkbenchSelection() {
  document.addEventListener("selection-change", (e: Event) => {
    const detail = (e as CustomEvent).detail;
    const rows = detail?.selectedRows ?? [];
    if (!rows.length) return;
    const row = rows[0];

    const cells: Record<string, string> = {};
    for (const col of ["id", "grade", "eventType", "patientId", "siteName",
        "slaTimeRemainingHours", "escalationStatus", "regulatorySubmissionStatus",
        "deviationType", "severity", "piApprovalStatus", "irbDecision", "reportedAt"]) {
      try {
        const cell = row.cell(col);
        if (cell && cell.type !== "NULL") cells[col] = String(cell.value);
      } catch { /* column not present */ }
    }

    if (cells.grade) {
      updateSafetyWorkbench(cells.id ?? "", cells);
    } else if (cells.deviationType) {
      updateProtocolWorkbench(cells.id ?? "", cells);
    }
  });
}

function esc(s: string): string {
  const el = document.createElement("span");
  el.textContent = s;
  return el.innerHTML;
}

function updateSafetyWorkbench(aeId: string, data: Record<string, string>) {
  if (!aeId) return;

  const overview = document.getElementById("ae-overview");
  if (overview) {
    overview.innerHTML = `
      <dl style="display:grid; grid-template-columns: max-content 1fr; gap: 0.5rem 1rem; padding: 1rem; margin: 0;">
        <dt style="font-weight:600; color: var(--pages-neutral-11);">Grade</dt><dd>${esc(data.grade ?? "—")}</dd>
        <dt style="font-weight:600; color: var(--pages-neutral-11);">Event Type</dt><dd>${esc(data.eventType ?? "—")}</dd>
        <dt style="font-weight:600; color: var(--pages-neutral-11);">Patient</dt><dd>${esc(data.patientId ? data.patientId.substring(0, 8) + "..." : "—")}</dd>
        <dt style="font-weight:600; color: var(--pages-neutral-11);">Site</dt><dd>${esc(data.siteName ?? "—")}</dd>
        <dt style="font-weight:600; color: var(--pages-neutral-11);">SLA Remaining</dt><dd>${esc(data.slaTimeRemainingHours ? data.slaTimeRemainingHours + "h" : "—")}</dd>
        <dt style="font-weight:600; color: var(--pages-neutral-11);">Escalation</dt><dd>${esc(data.escalationStatus ?? "—")}</dd>
        <dt style="font-weight:600; color: var(--pages-neutral-11);">IND Status</dt><dd>${esc(data.regulatorySubmissionStatus ?? "—")}</dd>
      </dl>`;
  }

  const susarGate = document.getElementById("susar-gate") as any;
  if (susarGate) {
    susarGate.endpoint = `/api/trials/${trialId}/adverse-events/${aeId}/governance`;
    susarGate.setAttribute("gate-id", aeId);
  }

  const precedents = document.getElementById("ae-precedents") as any;
  if (precedents) precedents.endpoint = `/api/trials/${trialId}/adverse-events/${aeId}/precedents`;

  const auditTrail = document.getElementById("ae-audit-trail") as any;
  if (auditTrail) {
    auditTrail.setAttribute("trial-id", trialId);
    auditTrail.setAttribute("subject-id", aeId);
  }

  const gradeHistory = document.getElementById("ae-grade-history") as any;
  if (gradeHistory) gradeHistory.endpoint = `/api/trials/${trialId}/adverse-events/${aeId}/grade-history`;

  const regrade = document.getElementById("ae-regrade") as any;
  if (regrade) regrade.endpoint = `/api/trials/${trialId}/adverse-events/${aeId}/regrade`;
}

function updateProtocolWorkbench(devId: string, data: Record<string, string>) {
  if (!devId) return;

  const overview = document.getElementById("dev-overview");
  if (overview) {
    overview.innerHTML = `
      <dl style="display:grid; grid-template-columns: max-content 1fr; gap: 0.5rem 1rem; padding: 1rem; margin: 0;">
        <dt style="font-weight:600; color: var(--pages-neutral-11);">Type</dt><dd>${esc(data.deviationType ?? "—")}</dd>
        <dt style="font-weight:600; color: var(--pages-neutral-11);">Severity</dt><dd>${esc(data.severity ?? "—")}</dd>
        <dt style="font-weight:600; color: var(--pages-neutral-11);">Site</dt><dd>${esc(data.siteName ?? "—")}</dd>
        <dt style="font-weight:600; color: var(--pages-neutral-11);">PI Approval</dt><dd>${esc(data.piApprovalStatus ?? "—")}</dd>
        <dt style="font-weight:600; color: var(--pages-neutral-11);">IRB Decision</dt><dd>${esc(data.irbDecision ?? "—")}</dd>
        <dt style="font-weight:600; color: var(--pages-neutral-11);">Reported</dt><dd>${esc(data.reportedAt ? data.reportedAt.substring(0, 10) : "—")}</dd>
      </dl>`;
  }

  const commitment = document.getElementById("dev-commitment") as any;
  if (commitment) {
    commitment.commitmentId = devId;
    commitment.endpoint = `/api/trials/${trialId}/deviations/${devId}/commitment`;
  }

  const irbGate = document.getElementById("irb-gate") as any;
  if (irbGate) {
    irbGate.endpoint = `/api/trials/${trialId}/deviations/${devId}/irb-gate`;
    irbGate.setAttribute("gate-id", devId);
  }

  const precedents = document.getElementById("dev-precedents") as any;
  if (precedents) precedents.endpoint = `/api/trials/${trialId}/deviations/${devId}/precedents`;

  const auditTrail = document.getElementById("dev-audit-trail") as any;
  if (auditTrail) {
    auditTrail.setAttribute("trial-id", trialId);
    auditTrail.setAttribute("subject-id", devId);
  }
}

const container = document.getElementById("app");
if (container) {
  loadSite(container, app).then(() => {
    configureWorkItemInbox();
    configureWorkbenchSelection();
  }).catch((err) => {
    console.error("loadSite failed:", err);
    container.innerHTML = `<pre style="color:red;padding:2rem;">${err?.stack ?? err}</pre>`;
  });
}
