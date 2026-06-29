import { page, table, markdown, html, lookup } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { TRIAL_ID, ledgerEntriesDs } from "../datasets";
import { STEP8_NARRATIVE } from "../narrative";

// Site B and Patient B1 IDs from DemoDataSeeder — deterministic UUIDs
const SITE_B_ID = "28d71146-f562-3352-a521-2ede60adba82";
const PATIENT_B1_ID = "4bb87f70-ca9e-3ded-9bbc-df9bf6fbb38d";

/**
 * Step 8: The Proof
 *
 * Merkle verification showcase — the climax of the demo.
 *
 * Components:
 * - Narrative markdown
 * - Ledger entries table (timestamp, type, actor, summary)
 * - Merkle verification via <clinical-merkle-verify> web component
 * - Displays VERIFIED ✓ or FAILED ✗ with Merkle root hash
 *
 * The verification endpoint is GET /trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/ledger/verify.
 * Returns: { valid: boolean, merkleRoot: string | null }
 *
 * This endpoint exists in PatientResource and works for both pre-seeded and live-action entries because
 * the seeder uses real service calls that produce genuine Merkle chains.
 */
export const step8Proof = page("8. The Proof",
  markdown(`## Tamper-Evident Audit Trail\n\n${STEP8_NARRATIVE}`),

  // Ledger entries table
  table({
    sortable: true,
    columns: [
      { id: "occurredAt" as ColumnId, label: "Timestamp",
        expression: 'new Date(value).toLocaleString()' },
      { id: "entryType" as ColumnId, label: "Entry Type",
        expression: 'value.replace("LedgerEntry", "")' },
      { id: "actorId" as ColumnId, label: "Actor",
        expression: 'value.substring(0, 20) + (value.length > 20 ? "..." : "")' },
      { id: "subjectId" as ColumnId, label: "Subject ID",
        expression: 'value.substring(0, 8) + "..."' },
      { id: "digest" as ColumnId, label: "Digest (SHA-256)",
        expression: 'value.substring(0, 16) + "..."' }
    ],
    lookup: lookup("ledger-entries")
  }),

  // Merkle verification button and result display
  html(`<clinical-merkle-verify trial-id="${TRIAL_ID}" site-id="${SITE_B_ID}" patient-id="${PATIENT_B1_ID}"></clinical-merkle-verify>`),

  // Explanation: what this means
  markdown(`### Why This Matters

Every ledger entry — AE reports, SUSAR decisions, attestations, protocol deviations, PI approvals — is hashed and chained into a Merkle Mountain Range. The digest includes:

- **Entry content** — all domain fields serialized to bytes
- **Actor ID** — who performed the action
- **Timestamp** — when it occurred
- **Previous entry digest** — cryptographic link to the prior entry

**Independent verification:** The Merkle root can be published to an external system (blockchain, timestamping authority, regulatory archive). An auditor can verify the entire chain without trusting CaseHub's database — the cryptographic proof stands alone.

**EU AI Act Art.12 compliance:** Every AI-agent decision has a \`ComplianceSupplement\` attached, recording:
- Input data used
- Output decision produced
- Risk classification
- Human oversight applied
- Trust score at decision time

This is Layer 8 in action: **tamper-evident audit with regulatory compliance supplements.**`),

  // Final callout
  markdown(`### The Demo Is Complete

You've seen:

1. **Accountability (Layers 1-4):** PI COMMAND and Commitment lifecycle — qhorus
2. **AI Governance (Layers 5-7):** Trust routing, oversight gates, attestation feedback — engine + ledger
3. **The Proof (Layer 8):** Merkle verification, compliance supplements — regulatory foundation

CaseHub provides what no LLM pipeline can: **structurally guaranteed compliance** through formal commitments, trust-weighted routing, unconditional oversight gates, and cryptographic audit trails.

This is the AI Fusion demo — accountability meets AI governance.`),
  { datasets: [ledgerEntriesDs] }
);
