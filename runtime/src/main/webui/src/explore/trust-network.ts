import { page, columns, metric, table, markdown, lookup, groupBy, col, avg, count } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { agentsDs } from "../datasets";

/**
 * Explore Mode: Trust Network
 *
 * Agent trust scores, capabilities, and decision history.
 *
 * Components:
 * - Aggregate trust health metrics (3-column row)
 * - Agent table with capability, trust score, dimension, phase, decisions, endorsement ratio
 */
export const trustNetwork = page("Trust Network",
  markdown(`## AI Agent Trust Network

CaseHub routes high-stakes decisions to trusted agents based on Bayesian Beta trust scores. Each agent's score is computed from its track record of endorsed vs challenged attestations.

**Trust Dimensions:**
- **safety-accuracy** — adverse event classification correctness
- **eligibility-precision** — false positive rate on patient screening
- **protocol-adherence** — deviation detection accuracy

**Maturity Phases:**
- **bootstrap** — <10 decisions, priors dominate
- **emerging** — 10-50 decisions, priors still influential
- **established** — 50+ decisions, empirical track record dominates`),

  // 3-column aggregate trust metrics
  columns(
    { span: 4 },
    metric({
      title: "Avg Trust Score",
      lookup: lookup(agentsDs, [], [groupBy([], [avg("trustScore")])])
    }),
    { span: 4 },
    metric({
      title: "Total Decisions",
      lookup: lookup(agentsDs, [], [groupBy([], [count("capability")])])
    }),
    { span: 4 },
    metric({
      title: "Active Agents",
      lookup: lookup(agentsDs, [], [groupBy([], [count("capability")])])
    })
  ),

  // Agent table
  table({
    sortable: true,
    pageSize: 25,
    columns: [
      { id: "capability" as ColumnId, label: "Capability",
        expression: 'value.replace(/-/g, " ").toLowerCase().replace(/\\b\\w/g, l => l.toUpperCase())' },
      { id: "trustScore" as ColumnId, label: "Trust Score",
        expression: `
          const score = parseFloat(value);
          if (score >= 0.8) return "🟢 " + score.toFixed(3);
          if (score >= 0.6) return "🟡 " + score.toFixed(3);
          if (score >= 0.4) return "🟠 " + score.toFixed(3);
          return "🔴 " + score.toFixed(3);
        ` },
      { id: "trustDimension" as ColumnId, label: "Trust Dimension",
        expression: 'value.replace(/-/g, " ").toLowerCase().replace(/\\b\\w/g, l => l.toUpperCase())' },
      { id: "maturityPhase" as ColumnId, label: "Maturity",
        expression: `
          if (value === "bootstrap") return "🔵 Bootstrap";
          if (value === "emerging") return "🟡 Emerging";
          if (value === "established") return "🟢 Established";
          return value;
        ` },
      { id: "totalDecisions" as ColumnId, label: "Total Decisions" },
      { id: "endorsementRatio" as ColumnId, label: "Endorsement Ratio",
        expression: `
          const ratio = parseFloat(value);
          if (isNaN(ratio)) return "—";
          return (ratio * 100).toFixed(1) + "%";
        ` }
    ],
    lookup: lookup(agentsDs, [], [])
  })
);
