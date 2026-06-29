import { page, columns, metric, table, markdown, lookup, groupBy, col } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { TRIAL_ID, deviationsDs } from "../datasets";
import { STEP3_NARRATIVE } from "../narrative";
import { actionButton } from "../helpers";

// Site B ID from DemoDataSeeder — deterministic UUID
const SITE_B_ID = "28d71146-f562-3352-a521-2ede60adba82"; // UUID.nameUUIDFromBytes("SITE-B".getBytes(UTF_8))

/**
 * Step 3: Event — Protocol Deviation Reported
 *
 * Components:
 * - Narrative markdown
 * - Action button: "Report Protocol Deviation" → POST CRITICAL deviation at Site B
 * - Deviations table: type, severity, piApprovalStatus, commandedAt
 * - Commitment lifecycle display: COMMANDED → ...
 *
 * Action button uses native action-button component from casehub-pages.
 */
export const step3Deviation = page("3. Protocol Deviation",
  markdown(`## Protocol Deviation Event\n\n${STEP3_NARRATIVE}`),

  // Action button: report CRITICAL deviation at Site B
  actionButton({
    label: "Report CRITICAL Protocol Deviation",
    url: `/trials/${TRIAL_ID}/sites/${SITE_B_ID}/deviations`,
    method: "POST",
    body: { deviationType: "DOSING_ERROR", severity: "CRITICAL" },
    style: "danger",
    confirm: "This will report a CRITICAL protocol deviation at Site B. Continue?",
    onSuccess: { refresh: ["deviations"], message: "CRITICAL deviation reported — PI COMMANDED" }
  }),

  // Deviations table
  table({
    sortable: true,
    columns: [
      { id: "deviationType" as ColumnId, label: "Type" },
      { id: "severity" as ColumnId, label: "Severity" },
      { id: "piApprovalStatus" as ColumnId, label: "PI Approval Status",
        expression: 'value === "COMMANDED" ? "⚠️ COMMANDED" : value === "APPROVED" ? "✅ APPROVED" : value === "ESCALATED" ? "🔼 ESCALATED" : value' },
      { id: "commandedAt" as ColumnId, label: "Commanded At",
        expression: 'value != null ? new Date(value).toLocaleString() : "—"' },
      { id: "respondedAt" as ColumnId, label: "Responded At",
        expression: 'value != null ? new Date(value).toLocaleString() : "—"' }
    ],
    lookup: lookup("deviations")
  }),

  // Commitment lifecycle summary
  columns(
    [4, 8],
    [metric({
      title: "COMMANDED Deviations",
      lookup: lookup(
        "deviations",
        groupBy(null, col("piApprovalStatus"))
      )
    })],
    [markdown(`### Commitment Lifecycle

The platform sends a formal **COMMAND** to the named Principal Investigator — not a notification, an obligation. A Commitment is created with a 24-hour deadline. If the PI doesn't respond, the platform escalates automatically.

**Status:**
- ⚠️ **COMMANDED** — PI obligation created, deadline active
- ✅ **APPROVED** — PI responded, Commitment closed
- 🔼 **ESCALATED** — IRB committee review triggered (CRITICAL deviations only)`)]
  ),
  { datasets: [deviationsDs] }
);
