import { page, columns, metric, table, markdown, lookup, groupBy, count } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { agentsDs } from "../datasets";
import { STEP2_NARRATIVE } from "../narrative";

/**
 * Step 2: Meet the AI Agents
 *
 * Components:
 * - Narrative markdown
 * - Agents table: capability, trust score, dimension, maturity phase, decisions, endorsements
 * - Trust routing policy summary (static markdown table)
 * - 3 metric cards: gated action types, trust dimensions, oversight policy
 */
export const step2Agents = page("2. Meet the AI Agents",
  markdown(`## Trust-Weighted Agent Governance\n\n${STEP2_NARRATIVE}`),

  // Agents table
  table({
    sortable: true,
    columns: [
      { id: "capability" as ColumnId, label: "Capability" },
      { id: "trustScore" as ColumnId, label: "Trust Score", expression: 'value != null ? value.toFixed(3) : "—"' },
      { id: "trustDimension" as ColumnId, label: "Trust Dimension" },
      { id: "maturityPhase" as ColumnId, label: "Maturity", expression: 'value === 2 ? "Mature" : value === 1 ? "Learning" : "Bootstrap"' },
      { id: "decisionCount" as ColumnId, label: "Decisions" },
      { id: "attestationPositive" as ColumnId, label: "Endorsed" },
      { id: "attestationNegative" as ColumnId, label: "Challenged" }
    ],
    lookup: lookup(agentsDs, [], [])
  }),

  // Trust routing policy summary (static markdown table from ClinicalTrustRoutingPolicyProvider)
  markdown(`### Trust Routing Policy

| Capability | Minimum Threshold | Below Threshold Behavior |
|------------|------------------|-------------------------|
| eligibility-screening | 0.60 | Route to human reviewer |
| safety-monitoring | 0.70 | Escalate to PI |
| protocol-review | 0.65 | Human oversight required |
| irb-consultation | 0.75 | Committee review only |
| data-safety-monitoring | 0.75 | DSMB escalation |
| regulatory-submission | 0.80 | Manual submission path |

*Trust scores are Bayesian Beta distributions updated with each attestation. Agents below threshold cannot act autonomously.*`),

  // 3 metric cards: gated types, trust dimensions, oversight policy
  columns(
    { span: 4 },
    metric({
      title: "Gated Capabilities",
      lookup: lookup(agentsDs, [], [groupBy([], [count("capability", "capability", "#")])])
    }),
    { span: 4 },
    markdown(`**Trust Dimensions Tracked**\n\n3 dimensions: safety-accuracy, eligibility-precision, protocol-adherence`),
    { span: 4 },
    markdown(`**Oversight Policy**\n\nNo autonomous safety decisions — all high-stakes actions gated`)
  )
);
