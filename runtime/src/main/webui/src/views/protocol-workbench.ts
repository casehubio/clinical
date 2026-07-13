import {
  columns, table, tabs, panel, markdown, html,
  lookup,
} from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import { deviationsDs, deviationPrecedentsDs, ledgerEntriesDs, TRIAL_ID, DEMO_MODE } from "../datasets.js";
import { auditTrailStub } from "../stubs/audit-trail-viewer.js";

export function protocolWorkbench(): Component {
  const deviationTable = table({
    title: "Protocol Deviations",
    lookup: lookup(deviationsDs.id),
    sortable: true,
    pageSize: 25,
    columns: [
      { id: "deviationType" as never, name: "Type" },
      { id: "severity" as never, name: "Severity", expression: 'value = "CRITICAL" ? "🔴 CRITICAL" : value = "MAJOR" ? "🟠 MAJOR" : "🟡 MINOR"' },
      { id: "siteName" as never, name: "Site" },
      { id: "piApprovalStatus" as never, name: "PI Approval", expression: 'value = "COMMANDED" ? "⏳ COMMANDED" : value = "APPROVED" ? "✅ APPROVED" : value = "DECLINED" ? "❌ DECLINED" : value = "EXPIRED" ? "⏰ EXPIRED" : value' },
      { id: "irbStatus" as never, name: "IRB Decision", expression: 'value = "APPROVED" ? "✅ APPROVED" : value = "REJECTED" ? "❌ REJECTED" : value = "PENDING" ? "⏳ PENDING" : value ? value : "—"' },
      { id: "reportedAt" as never, name: "Reported", expression: 'value ? $substring(value, 0, 10) : ""' },
    ],
    rowStyle: [
      { condition: '#{row.severity} == "CRITICAL"', style: { "background-color": "var(--pages-red-2, #fdf0f0)" } },
      { condition: '#{row.piApprovalStatus} == "EXPIRED"', style: { "background-color": "var(--pages-red-2, #fdf0f0)" } },
    ],
    filter: { enabled: true },
    emptyMessage: "No protocol deviations recorded",
  });

  const detailTabs = tabs(
    ["Overview", panel("Deviation Overview",
      markdown("Select a protocol deviation from the list to view details."),
    )],
    ["PI Commitment", panel("PI Commitment Lifecycle",
      html(`<commitment-lifecycle
        endpoint="/api/trials/${TRIAL_ID}/deviations/{devId}/commitment"
      ></commitment-lifecycle>`),
    )],
    ["IRB Review", panel("IRB Review",
      markdown("IRB committee review status."),
      html(`<approval-gate
        endpoint="/demo/deviations/{deviationId}/approve-irb"
        prompt="Review protocol deviation for IRB approval"
        context-text="CRITICAL protocol deviation requires ethics committee review — 72h deadline"
        require-confirmation
      ></approval-gate>`),
    )],
    ["Precedents", panel("Similar Past Deviations",
      html(`<cbr-precedents-panel
        endpoint="/api/trials/${TRIAL_ID}/deviations/dev-demo-001/precedents"
        empty-message="No similar deviations found in case memory"
        ${DEMO_MODE ? "demo" : ""}
      ></cbr-precedents-panel>`),
    )],
    ["Audit Trail", panel("Ledger Entries",
      auditTrailStub(ledgerEntriesDs.id),
    )],
  );

  return columns([5, 7],
    [deviationTable],
    [detailTabs],
  );
}

export const protocolWorkbenchDatasets = [deviationsDs, deviationPrecedentsDs, ledgerEntriesDs];
