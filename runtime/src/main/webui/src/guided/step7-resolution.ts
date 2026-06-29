import { page, columns, metric, markdown, html, lookup, groupBy, col } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { TRIAL_ID, adverseEventsDs } from "../datasets";
import { STEP7_NARRATIVE } from "../narrative";

/**
 * Step 7: Resolution & Trust Update
 *
 * Components:
 * - Narrative markdown
 * - Action button: "Approve SUSAR Determination" → POST to demo endpoint
 * - Gate decision display (APPROVED with investigator ID)
 * - Attestation card (ENDORSED → safety-accuracy dimension)
 * - Trust score before/after metrics
 * - Regulatory submission status
 *
 * The demo endpoint completes the SUSAR oversight gate WorkItem, triggering the real chain:
 * ActionGateApprovedEvent → SusarGateDecisionListener → SusarAgentAttestationWriter → TrustScoreJob.
 *
 * The AE ID is discovered by fetching adverse events and finding the first with escalationStatus === 'REQUESTED'.
 */
export const step7Resolution = page("7. Resolution & Trust Update",
  markdown(`## Gate Approval & Trust Score Update\n\n${STEP7_NARRATIVE}`),

  // Action button with idempotency check
  html(`<clinical-susar-gate trial-id="${TRIAL_ID}"></clinical-susar-gate>`),

  // Metrics: trust score before/after, gate status, attestation
  columns(
    [3, 3, 3, 3],
    [metric({
      title: "SUSAR Gates Completed",
      lookup: lookup(
        "adverse-events",
        groupBy(null, col("escalationStatus"))
      )
    })],
    [markdown(`### Attestation Recorded

When the investigator approves the SUSAR determination, CaseHub writes a **LedgerAttestation** entry with verdict ENDORSED. This feeds into the agent's Bayesian trust score computation.`)],
    [markdown(`### Trust Score Recomputation

The demo endpoint triggers \`TrustScoreJob.runComputation()\` immediately after the attestation is written. The trust score delta is computed from the new attestation — good decisions build trust.`)],
    [markdown(`### Regulatory Submission

The IND report work item is created automatically with a 7-day deadline (21 CFR 312.32). The regulatory team receives the work item via routing policy.`)]
  ),

  // Explanation
  markdown(`### The Trust Feedback Loop

This is Layer 7 in action: trust-weighted routing with attestation feedback. The agent was selected by trust score, its decision was gated, and the investigator's approval became an attestation that updated the trust score.

**Key insight:** Trust scores are not static agent metadata — they are continuously refined by human attestations. The platform learns which agents make reliable decisions over time.`),
  { datasets: [adverseEventsDs] }
);
