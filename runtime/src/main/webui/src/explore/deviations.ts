import { page, table, markdown, lookup, sortBy } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { deviationsDs } from "../datasets";

/**
 * Explore Mode: Protocol Deviations
 *
 * All protocol deviations across the trial with PI approval status and commitment state.
 *
 * Components:
 * - Deviation table with type, severity, site, PI approval status, commitment state
 * - Status expressions for readable display
 */
export const deviations = page("Protocol Deviations",
  markdown(`## Protocol Deviations Registry

All protocol deviations require Principal Investigator approval via formal COMMAND. CRITICAL deviations escalate to IRB committee review after PI authorization.

**Commitment Lifecycle:**
- COMMANDED → PI receives formal obligation with deadline
- APPROVED/DECLINED → PI responds, commitment closes
- ESCALATED → CRITICAL deviations trigger IRB consultation
- RESOLVED → IRB decision recorded, deviation complete`),

  // Deviations table
  table({
    sortable: true,
    pageSize: 25,
    columns: [
      { id: "deviationType" as ColumnId, label: "Deviation Type",
        expression: 'value.replace(/_/g, " ").toLowerCase().replace(/\\b\\w/g, l => l.toUpperCase())' },
      { id: "severity" as ColumnId, label: "Severity",
        expression: `
          if (value === "CRITICAL") return "🔴 CRITICAL";
          if (value === "MAJOR") return "🟠 MAJOR";
          if (value === "MINOR") return "🟡 MINOR";
          return value;
        ` },
      { id: "siteName" as ColumnId, label: "Site" },
      { id: "reportedAt" as ColumnId, label: "Reported",
        expression: 'new Date(value).toLocaleString()' },
      { id: "piApprovalStatus" as ColumnId, label: "PI Approval",
        expression: `
          if (value === "COMMANDED") return "⏳ COMMANDED";
          if (value === "APPROVED") return "✅ APPROVED";
          if (value === "DECLINED") return "❌ DECLINED";
          if (value === "EXPIRED") return "⏰ EXPIRED";
          return value || "—";
        ` },
      { id: "commitmentState" as ColumnId, label: "Commitment State",
        expression: `
          if (value === "COMMANDED") return "🔵 COMMANDED";
          if (value === "RESOLVED") return "✅ RESOLVED";
          if (value === "ESCALATED") return "🔼 ESCALATED (IRB)";
          if (value === "DECLINED") return "❌ DECLINED";
          if (value === "EXPIRED") return "⏰ EXPIRED";
          return value || "—";
        ` },
      { id: "irbDecision" as ColumnId, label: "IRB Decision",
        expression: 'value || "—"' }
    ],
    lookup: lookup(deviationsDs, [], [sortBy("reportedAt", "DESC")])
  })
);
