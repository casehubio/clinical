import {
  dataTable, tabs, panel, html,
  lookup,
  dockWorkbench, hostPanel,
} from "@casehubio/pages-ui";
import type { Component, DockPanelConfig } from "@casehubio/pages-ui";
import type { DataSourceBinding } from "@casehubio/pages-data";

export function protocolWorkbench(trialId: string): Component {
  const deviationTable = dataTable({
    title: "Protocol Deviations",
    lookup: lookup("deviations"),
    sortable: true,
    pageSize: 25,
    selection: "single",
    selectionKey: "id",
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
      html(`<commitment-lifecycle id="dev-commitment" data-trial-id="${trialId}" data-source-dataset="deviations"></commitment-lifecycle>`),
    )],
    ["IRB Review", panel("IRB Review",
      html(`<blocks-approval-gate id="irb-gate"
        data-trial-id="${trialId}" data-source-dataset="deviations"
        prompt="Review protocol deviation for IRB approval"
        context-text="Protocol deviation requires ethics committee review — 72h deadline"
      ></blocks-approval-gate>`),
    )],
    ["Precedents", panel("Similar Past Deviations",
      dataTable({
        title: "Deviation Precedents",
        lookup: lookup("dev-precedents"),
        sortable: true,
        pageSize: 10,
        columns: [
          { id: "similarity" as never, name: "Similarity", expression: '$string($round($number(value))) & "%"' },
          { id: "severity" as never, name: "Severity" },
          { id: "outcome" as never, name: "Outcome" },
          { id: "resolutionTime" as never, name: "Resolution Time" },
          { id: "reportedDate" as never, name: "Reported" },
        ],
      }),
    )],
  );

  const conversationViewer: DockPanelConfig = {
    key: "conversation", label: "PI Deliberation", icon: "message-square",
    defaultOpen: false, content: hostPanel("conversation-viewer"),
  };
  const routingRationale: DockPanelConfig = {
    key: "routing", label: "Routing Rationale", icon: "git-branch",
    defaultOpen: false, content: hostPanel("routing-rationale"),
  };
  const auditTrail: DockPanelConfig = {
    key: "audit", label: "Audit Trail", icon: "file-text",
    defaultOpen: false, content: dataTable({
      title: "Audit Trail",
      lookup: lookup("ledger-entries"),
      sortable: true,
      pageSize: 25,
      columns: [
        { id: "occurredAt" as never, name: "Timestamp" },
        { id: "entryType" as never, name: "Type" },
        { id: "actorId" as never, name: "Actor" },
        { id: "subjectId" as never, name: "Subject" },
        { id: "digest" as never, name: "Digest", expression: 'value ? $substring(value, 0, 16) & "..." : ""' },
      ],
      filter: { enabled: true },
    }),
  };

  return dockWorkbench({
    centre: [deviationTable, detailTabs],
    right: [conversationViewer, routingRationale],
    bottom: [auditTrail],
  });
}

export const protocolWorkbenchDatasets: DataSourceBinding[] = [];
