import {
  columns, table, tabs, panel, markdown, html,
  lookup,
} from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import { adverseEventsDs, aePrecedentsDs, ledgerEntriesDs, TRIAL_ID, DEMO_MODE } from "../datasets.js";
import { auditTrailStub } from "../stubs/audit-trail-viewer.js";

export function safetyWorkbench(): Component {
  const aeTable = table({
    title: "Adverse Events",
    lookup: lookup(adverseEventsDs.id),
    sortable: true,
    pageSize: 25,
    columns: [
      { id: "grade" as never, name: "Grade", expression: '(value = "GRADE_4" or value = "GRADE_5") ? "🔴 " & value : value = "GRADE_3" ? "🟠 " & value : value' },
      { id: "eventType" as never, name: "Event Type" },
      { id: "patientId" as never, name: "Patient", expression: 'value ? $substring(value, 0, 8) & "..." : ""' },
      { id: "siteName" as never, name: "Site" },
      { id: "slaTimeRemainingHours" as never, name: "SLA Remaining", expression: '$number(value) < 0 ? "🔴 OVERDUE" : $number(value) < 4 ? "🟠 " & $string($round($number(value))) & "h" : $number(value) < 12 ? "🟡 " & $string($round($number(value))) & "h" : "🟢 " & $string($round($number(value))) & "h"' },
      { id: "escalationStatus" as never, name: "Escalation" },
      { id: "regulatorySubmissionStatus" as never, name: "IND Status" },
    ],
    rowStyle: [
      { condition: '#{row.grade} == "GRADE_4" || #{row.grade} == "GRADE_5"', style: { "background-color": "var(--pages-red-2, #fdf0f0)" } },
      { condition: '#{row.slaTimeRemainingHours} < 0', style: { "background-color": "var(--pages-red-3, #fde0e0)" } },
    ],
    filter: { enabled: true },
    emptyMessage: "No adverse events reported",
  });

  const detailTabs = tabs(
    ["Overview", panel("AE Overview",
      markdown("Select an adverse event from the list to view details."),
    )],
    ["SUSAR Evaluation", panel("SUSAR Evaluation",
      markdown("SUSAR criteria assessment and approval gate."),
      html(`<approval-gate
        endpoint="/demo/adverse-events/{aeId}/approve-susar-gate"
        prompt="Review SUSAR determination for this adverse event"
        context-text="Grade 4+ unexpected suspected adverse reaction — SUSAR criteria evaluation"
        require-confirmation
      ></approval-gate>`),
    )],
    ["Trust & Attestation", panel("Trust Feedback",
      html(`<trust-feedback-display></trust-feedback-display>`),
    )],
    ["Regulatory", panel("Regulatory Status",
      html(`<sla-breach-policy-indicator></sla-breach-policy-indicator>`),
    )],
    ["Precedents", panel("Similar Past Cases",
      html(`<cbr-precedents-panel
        endpoint="/api/trials/${TRIAL_ID}/adverse-events/ae-demo-001/precedents"
        empty-message="No similar adverse events found in case memory"
        ${DEMO_MODE ? "demo" : ""}
      ></cbr-precedents-panel>`),
    )],
    ["Audit Trail", panel("Ledger Entries",
      auditTrailStub(ledgerEntriesDs.id),
    )],
  );

  return columns([5, 7],
    [aeTable],
    [detailTabs],
  );
}

export const safetyWorkbenchDatasets = [adverseEventsDs, aePrecedentsDs, ledgerEntriesDs];
