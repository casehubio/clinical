import { page, columns, metric, table, markdown, lookup, groupBy, col } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { TRIAL_ID, adverseEventsDs } from "../datasets";
import { STEP5_NARRATIVE } from "../narrative";
import { actionButton } from "../helpers";

// Site B and Patient B1 IDs from DemoDataSeeder — deterministic UUIDs
const SITE_B_ID = "28d71146-f562-3352-a521-2ede60adba82"; // UUID.nameUUIDFromBytes("SITE-B".getBytes(UTF_8))
const PATIENT_B1_ID = "4bb87f70-ca9e-3ded-9bbc-df9bf6fbb38d"; // UUID.nameUUIDFromBytes("PATIENT-B-001".getBytes(UTF_8))

/**
 * Step 5: Event — Grade 4 AE Reported
 *
 * Components:
 * - Narrative markdown (includes DSMB rollup callout)
 * - Action button: "Report Adverse Event" → POST Grade 4 unexpected AE at Site B
 * - AE detail display with SLA deadline
 * - AE table with polling (3s refresh during active scenario)
 *
 * Action button uses native action-button component from casehub-pages.
 *
 * DSMB rollup context: The seeder leaves grade4Active flags set on the trial case blackboard for
 * the seeded Grade 4 AEs at Site A. When this new Grade 4 AE fires at Site B, the trial case now
 * sees ≥2 sites with simultaneous Grade 4+ signals — the DSMB rollup binding fires automatically.
 */
export const step5AeEvent = page("5. Grade 4 AE Reported",
  markdown(`## Grade 4 Adverse Event\n\n${STEP5_NARRATIVE}

**Notice:** The platform detected a cross-site safety pattern. Two sites now have active Grade 4+ events — a DSMB review has been triggered automatically, with no site-level agent having global visibility.`),

  // Action button: report Grade 4 AE at Site B for Patient B1
  actionButton({
    label: "Report Grade 4 Adverse Event",
    url: `/trials/${TRIAL_ID}/sites/${SITE_B_ID}/patients/${PATIENT_B1_ID}/adverse-events`,
    method: "POST",
    body: { grade: "GRADE_4", occurredAt: new Date().toISOString(), unexpected: true, suspected: true },
    style: "danger",
    confirm: "This will report a Grade 4 hepatotoxicity event at Site B. Continue?",
    onSuccess: { refresh: ["adverse-events"], message: "Grade 4 AE reported — 24h SLA activated" }
  }),

  // AE detail cards (metrics)
  columns(
    [3, 3, 3, 3],
    [metric({
      title: "Grade 4+ AEs",
      lookup: lookup(
        "adverse-events",
        groupBy(null, col("grade"))
      )
    })],
    [metric({
      title: "SUSAR Oversight Active",
      lookup: lookup(
        "adverse-events",
        groupBy(null, col("escalationStatus"))
      )
    })],
    [metric({
      title: "Escalated AEs",
      lookup: lookup(
        "adverse-events",
        groupBy(null, col("escalationStatus"))
      )
    })],
    [markdown(`### 24h SLA Active

Grade 4 adverse events trigger a 24-hour SLA work item under ICH E6(R3) §5.17. The platform tracks deadlines automatically — no human monitoring required.`)]
  ),

  // AE table
  table({
    sortable: true,
    columns: [
      { id: "enrollmentId" as ColumnId, label: "Patient",
        expression: 'value.substring(0, 8) + "..."' },
      { id: "grade" as ColumnId, label: "Grade",
        expression: 'value === "GRADE_4" ? "🔴 GRADE 4" : value === "GRADE_5" ? "⚫ GRADE 5" : value' },
      { id: "type" as ColumnId, label: "Type" },
      { id: "escalationStatus" as ColumnId, label: "Escalation",
        expression: 'value === "NONE" ? "—" : value === "REQUESTED" ? "⚠️ REQUESTED" : value' },
      { id: "regulatorySubmissionStatus" as ColumnId, label: "Regulatory" },
      { id: "slaTimeRemaining" as ColumnId, label: "SLA Remaining" },
      { id: "reportedAt" as ColumnId, label: "Reported",
        expression: 'new Date(value).toLocaleString()' }
    ],
    lookup: lookup("adverse-events")
  }),

  // DSMB rollup callout
  markdown(`### DSMB Cross-Site Detection

The seeded Grade 4 AEs at Site A left \`grade4Active\` flags set on the trial case blackboard. When this new Grade 4 AE fired at Site B, the trial case detected ≥2 sites with simultaneous Grade 4+ signals — triggering a DSMB review binding automatically.

**This is Layer 6 in action:** trial-level blackboard aggregation with cross-site pattern detection. No site-level agent has global visibility — the engine detects the pattern from accumulated context.`),
  { datasets: [adverseEventsDs] }
);
