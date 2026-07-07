import { page, table, markdown, lookup, sortBy } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { deviationsDs } from "../datasets";
import { TRIAL_ID } from "../datasets";
import { dataset } from "@casehubio/pages-ui";

/**
 * Explore Mode: Protocol Deviations
 *
 * All protocol deviations across the trial with PI approval status and commitment state.
 *
 * Components:
 * - Deviation table with type, severity, site, PI approval status, commitment state
 * - Status expressions for readable display
 * - Past Similar Cases section with CBR precedents (static demo)
 *
 * NOTE: Full row-selection UX for precedents pending casehub-pages DSL enhancements.
 * Currently shows precedents for a hardcoded demo deviation as proof of concept.
 */

// Hardcoded deviation ID for precedents demo - will be replaced with actual ID from test data
// TODO: Make dynamic via row selection when casehub-pages DSL supports it
const DEMO_DEV_ID = "dev-demo-001";
const deviationPrecedentsDs = dataset("deviation-precedents", `/trials/${TRIAL_ID}/deviations/${DEMO_DEV_ID}/precedents`);

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
        expression: 'value ? new Date(value).toLocaleString() : "—"' },
      { id: "piApprovalStatus" as ColumnId, label: "PI Approval",
        expression: `
          if (value === "COMMANDED") return "⏳ COMMANDED";
          if (value === "APPROVED") return "✅ APPROVED";
          if (value === "DECLINED") return "❌ DECLINED";
          if (value === "EXPIRED") return "⏰ EXPIRED";
          return value || "—";
        ` },
      { id: "irbDecision" as ColumnId, label: "IRB Decision",
        expression: `
          if (!value || value === "PENDING") return "—";
          if (value === "APPROVED") return "✅ APPROVED";
          if (value === "REJECTED") return "❌ REJECTED";
          if (value === "DEFERRED") return "⏳ DEFERRED";
          if (value === "EXPIRED") return "⏰ EXPIRED";
          return value;
        ` }
    ],
    lookup: lookup("deviations", sortBy("reportedAt", "DESC"))
  }),

  markdown(`---

## Past Similar Cases (CBR Analysis)

Similar historical protocol deviations identified by the Case-Based Reasoning system.

_Note: Currently showing precedents for a demo deviation. Full row-selection UX pending DSL enhancements._`),

  // Precedents table
  table({
    sortable: true,
    pageSize: 10,
    columns: [
      { id: "score" as ColumnId, label: "Similarity",
        expression: '(value * 100).toFixed(1) + "%"' },
      { id: "deviationType" as ColumnId, label: "Deviation Type",
        expression: 'value.replace(/_/g, " ").toLowerCase().replace(/\\b\\w/g, l => l.toUpperCase())' },
      { id: "severity" as ColumnId, label: "Severity",
        expression: `
          if (value === "CRITICAL") return "🔴 CRITICAL";
          if (value === "MAJOR") return "🟠 MAJOR";
          if (value === "MINOR") return "🟡 MINOR";
          return value;
        ` },
      { id: "outcome" as ColumnId, label: "Outcome" },
      { id: "resolutionTime" as ColumnId, label: "Resolution (hrs)",
        expression: 'value !== null ? value.toFixed(1) : "—"' },
      { id: "reportedAt" as ColumnId, label: "Reported",
        expression: 'value ? new Date(value).toLocaleString() : "—"' }
    ],
    lookup: lookup("deviation-precedents", sortBy("score", "DESC"))
  }),

  { datasets: [deviationsDs, deviationPrecedentsDs] }
);
