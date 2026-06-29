import { page, columns, metric, table, markdown, lookup, groupBy, filterBy, col, html } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { TRIAL_ID, deviationsDs, ledgerEntriesDs } from "../datasets";
import { STEP4_NARRATIVE } from "../narrative";

/**
 * Step 4: PI Authorisation & Commitment
 *
 * Components:
 * - Narrative markdown
 * - Action button: "Approve as PI" → POST to demo endpoint
 * - Commitment lifecycle display: COMMANDED → APPROVED → ESCALATED
 * - Deviation Merkle chain: COMMAND entry → resolution entry → IRB entry
 *
 * Action button uses <clinical-pi-approval> web component to call /demo/deviations/{id}/approve-pi.
 * Button is enabled only if a COMMANDED deviation exists.
 *
 * After approval, the qhorus MessageReceivedEvent fires → PiResponseListener updates piApprovalStatus
 * to APPROVED, then (for CRITICAL deviations) ESCALATED.
 */
export const step4PiAuth = page("4. PI Authorisation",
  markdown(`## PI Authorisation & Commitment Lifecycle\n\n${STEP4_NARRATIVE}`),

  // Action button: Approve as PI
  html(`<clinical-pi-approval trial-id="${TRIAL_ID}"></clinical-pi-approval>`),

  // Deviations table with lifecycle status
  table({
    sortable: true,
    columns: [
      { id: "deviationType" as ColumnId, label: "Type" },
      { id: "severity" as ColumnId, label: "Severity" },
      { id: "piApprovalStatus" as ColumnId, label: "Commitment Lifecycle",
        expression: 'value === "COMMANDED" ? "1️⃣ COMMANDED" : value === "APPROVED" ? "2️⃣ APPROVED" : value === "ESCALATED" ? "3️⃣ ESCALATED" : value' },
      { id: "commandedAt" as ColumnId, label: "Commanded At",
        expression: 'value != null ? new Date(value).toLocaleString() : "—"' }
    ],
    lookup: lookup("deviations")
  }),

  // Merkle chain: deviation-related ledger entries
  markdown(`### Tamper-Evident Audit Trail

Every step is recorded in the Merkle ledger — COMMAND sent, PI response received, IRB escalation triggered. Each entry is independently verifiable.`),

  table({
    sortable: true,
    pageSize: 10,
    columns: [
      { id: "occurredAt" as ColumnId, label: "Timestamp",
        expression: 'new Date(value).toLocaleString()' },
      { id: "entryType" as ColumnId, label: "Event Type" },
      { id: "actorId" as ColumnId, label: "Actor" },
      { id: "sequenceNumber" as ColumnId, label: "Seq #" }
    ],
    lookup: lookup(
      "ledger-entries",
      filterBy("entryType", "CONTAINS", "DEVIATION")
    )
  }),

  // Commitment lifecycle explanation
  columns(
    [6, 6],
    [markdown(`### Commitment Lifecycle Stages

1️⃣ **COMMANDED** — Platform sends formal COMMAND to PI with 24h deadline
2️⃣ **APPROVED** — PI responds, Commitment closed
3️⃣ **ESCALATED** — CRITICAL deviations trigger IRB review (72h deadline)`)],
    [markdown(`### Why This Matters

**No LLM pipeline can provide this:** Every obligation is tracked with a named actor, a deadline, and a tamper-evident record. If the PI doesn't respond, the platform escalates automatically — no human in the loop needed for SLA enforcement.

This is **qhorus** — formal accountability for agentic systems.`)]
  ),
  { datasets: [deviationsDs, ledgerEntriesDs] }
);
