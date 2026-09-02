import {
  columns, dataTable, tabs, panel, html,
  lookup,
} from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import type { DataSourceBinding } from "@casehubio/pages-data";

export function protocolWorkbench(): Component {
  const deviationTable = dataTable({
    title: "Protocol Deviations",
    lookup: lookup("deviations"),
    sortable: true,
    pageSize: 25,
    selection: "single",
    columns: [
      { id: "deviationType" as never, name: "Type" },
      { id: "severity" as never, name: "Severity", expression: 'value = "CRITICAL" ? "🔴 CRITICAL" : value = "MAJOR" ? "🟠 MAJOR" : "🟡 MINOR"' },
      { id: "siteName" as never, name: "Site" },
      { id: "piApprovalStatus" as never, name: "PI Approval", expression: 'value = "COMMANDED" ? "⏳ COMMANDED" : value = "APPROVED" ? "✅ APPROVED" : value = "DECLINED" ? "❌ DECLINED" : value = "EXPIRED" ? "⏰ EXPIRED" : value' },
      { id: "irbDecision" as never, name: "IRB Decision", expression: 'value = "APPROVED" ? "✅ APPROVED" : value = "REJECTED" ? "❌ REJECTED" : value = "PENDING" ? "⏳ PENDING" : value ? value : "—"' },
      { id: "reportedAt" as never, name: "Reported", expression: 'value ? $substring(value, 0, 10) : ""' },
    ],
    rowStyle: [
      { condition: '#{row.severity} == "CRITICAL"', style: { "background-color": "var(--pages-red-2, #fdf0f0)" } },
      { condition: '#{row.piApprovalStatus} == "EXPIRED"', style: { "background-color": "var(--pages-red-2, #fdf0f0)" } },
    ],
    filter: { enabled: true },

  });

  const detailTabs = tabs(
    ["Overview", panel("Deviation Overview",
      html('<div id="dev-overview"><p style="color: var(--pages-neutral-9); font-style: italic; padding: 1rem;">Select a protocol deviation from the list to view details.</p></div>'),
    )],
    ["PI Commitment", panel("PI Commitment Lifecycle",
      html('<commitment-lifecycle id="dev-commitment"></commitment-lifecycle>'),
    )],
    ["IRB Review", panel("IRB Review",
      html(`<blocks-approval-gate id="irb-gate"
        prompt="Review protocol deviation for IRB approval"
        context-text="Protocol deviation requires ethics committee review — 72h deadline"
      ></blocks-approval-gate>`),
    )],
    ["Precedents", panel("Similar Past Deviations",
      html('<cbr-precedents-panel id="dev-precedents" empty-message="No similar deviations found in case memory"></cbr-precedents-panel>'),
    )],
    ["Audit Trail", panel("Ledger Entries",
      html('<clinical-audit-trail id="dev-audit-trail"></clinical-audit-trail>'),
    )],
  );

  return columns([5, 7],
    [deviationTable],
    [detailTabs],
  );
}

export const protocolWorkbenchDatasets: DataSourceBinding[] = [];
