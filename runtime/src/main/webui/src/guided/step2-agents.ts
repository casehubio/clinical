import { page, columns, metric, table, markdown, lookup, groupBy, count, max } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { agentsDs } from "../datasets";
import { STEP2_NARRATIVE } from "../narrative";

export const step2Agents = page("2. Meet the AI Agents",
  markdown(`## Trust-Weighted Agent Governance\n\n${STEP2_NARRATIVE}`),

  table({
    sortable: true,
    columns: [
      { id: "capability" as ColumnId, label: "Capability" },
      { id: "trustScore" as ColumnId, label: "Trust Score", expression: 'value != null ? value.toFixed(3) : "—"' },
      { id: "threshold" as ColumnId, label: "Threshold", expression: 'value != null ? value.toFixed(2) : "—"' },
      { id: "trustDimension" as ColumnId, label: "Trust Dimension" },
      { id: "maturityPhase" as ColumnId, label: "Maturity", expression: 'value === 2 ? "Mature" : value === 1 ? "Learning" : "Bootstrap"' },
      { id: "decisionCount" as ColumnId, label: "Decisions" },
      { id: "endorsementRatio" as ColumnId, label: "Endorsement", expression: 'value ?? "—"' }
    ],
    lookup: lookup("agents")
  }),

  columns(
    [4, 4, 4],
    [metric({
      title: "Gated Capabilities",
      lookup: lookup("agents", groupBy(null, count("capability")))
    })],
    [metric({
      title: "Trust Dimensions",
      lookup: lookup("agents", groupBy(null, max("distinctTrustDimensions")))
    })],
    [markdown(`**Oversight Policy**\n\nNo autonomous safety decisions — all high-stakes actions gated`)]
  ),
  { datasets: [agentsDs] }
);
