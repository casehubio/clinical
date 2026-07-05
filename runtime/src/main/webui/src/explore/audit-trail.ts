import { page, table, markdown, html, selector, lookup, groupBy, col, filterBy } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { TRIAL_ID, ledgerEntriesDs } from "../datasets";

// Site B and Patient B1 IDs from DemoDataSeeder — deterministic UUIDs
const SITE_B_ID = "28d71146-f562-3352-a521-2ede60adba82";
const PATIENT_B1_ID = "4bb87f70-ca9e-3ded-9bbc-df9bf6fbb38d";

/**
 * Explore Mode: Audit Trail
 *
 * Complete ledger entry history with type filtering and Merkle verification.
 *
 * Components:
 * - Entry type selector (dropdown filter)
 * - Ledger entries table with timestamp, type, actor, subject, digest
 * - Merkle verification via <clinical-merkle-verify> web component
 */
export const auditTrail = page("Audit Trail",
  markdown(`## Tamper-Evident Audit Trail

Every action — AE reports, SUSAR decisions, attestations, protocol deviations, PI approvals — is recorded in a cryptographically-chained ledger. Each entry includes a SHA-256 digest linking it to the previous entry, forming a Merkle Mountain Range that ensures no entry can be altered without detection.

**Filter by entry type or verify the entire chain's integrity below.**`),

  // Entry type selector
  selector({
    subtype: "dropdown",
    selfApply: true,
    notification: true,
    lookup: lookup("ledger-entries", groupBy("entryType", col("entryType")))
  }),

  // Ledger entries table
  table({
    sortable: true,
    pageSize: 50,
    listening: true,
    columns: [
      { id: "occurredAt" as ColumnId, label: "Timestamp",
        expression: 'new Date(value).toLocaleString()' },
      { id: "entryType" as ColumnId, label: "Entry Type",
        expression: 'value.replace("LedgerEntry", "").replace(/([A-Z])/g, " $1").trim()' },
      { id: "actorId" as ColumnId, label: "Actor",
        expression: 'value.substring(0, 24) + (value.length > 24 ? "..." : "")' },
      { id: "subjectId" as ColumnId, label: "Subject ID",
        expression: 'value.substring(0, 8) + "..."' },
      { id: "digest" as ColumnId, label: "Digest (SHA-256)",
        expression: 'value ? value.substring(0, 16) + "..." : "—"' }
    ],
    lookup: lookup("ledger-entries")
  }),

  // Merkle verification button (trial-level verification using Patient B1 at Site B)
  html(`<clinical-merkle-verify trial-id="${TRIAL_ID}" site-id="${SITE_B_ID}" patient-id="${PATIENT_B1_ID}"></clinical-merkle-verify>`),
  { datasets: [ledgerEntriesDs] }
);
