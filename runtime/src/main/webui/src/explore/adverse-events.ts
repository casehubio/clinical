import { page, table, markdown, lookup, sortBy } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { adverseEventsDs } from "../datasets";
import { TRIAL_ID } from "../datasets";
import { dataset } from "@casehubio/pages-ui";

/**
 * Explore Mode: Adverse Events
 *
 * All adverse events across the trial with full detail columns.
 *
 * Components:
 * - AE table with grade, type, site, patient, timestamps, SLA status, escalation status
 * - Sortable by all columns
 * - SLA styling: overdue events show red emoji prefix
 * - Past Similar Cases section with CBR precedents (static demo with selector)
 *
 * NOTE: Full row-selection UX for precedents pending casehub-pages DSL enhancements.
 * Currently shows precedents for a hardcoded demo AE as proof of concept.
 */

// Hardcoded AE ID for precedents demo - will be replaced with actual ID from test data
// TODO: Make dynamic via row selection when casehub-pages DSL supports it
const DEMO_AE_ID = "ae-demo-001";
const aePrecedentsDs = dataset("ae-precedents", `/trials/${TRIAL_ID}/adverse-events/${DEMO_AE_ID}/precedents`);

export const adverseEvents = page("Adverse Events",
  markdown(`## Adverse Events Registry

Complete list of all adverse events across all trial sites. The SLA Time Remaining column highlights urgent cases requiring immediate attention.

**SLA Rules (ICH E6(R3) §5.17):**
- Grade 5 (Death): 1 hour internal notification
- Grade 3-4 (Serious): 24 hours regulatory submission
- Grade 1-2 (Mild/Moderate): 7 days documentation`),

  // AE table with ALL columns
  table({
    sortable: true,
    pageSize: 25,
    columns: [
      { id: "id" as ColumnId, label: "ID",
        expression: 'value.substring(0, 8)' },
      { id: "grade" as ColumnId, label: "Grade",
        expression: 'value' },
      { id: "eventType" as ColumnId, label: "Event Type",
        expression: 'value ? value.replace(/_/g, " ").toLowerCase().replace(/\\b\\w/g, l => l.toUpperCase()) : "—"' },
      { id: "siteName" as ColumnId, label: "Site" },
      { id: "patientId" as ColumnId, label: "Patient" },
      { id: "reportedAt" as ColumnId, label: "Reported",
        expression: 'new Date(value).toLocaleString()' },
      { id: "slaDeadline" as ColumnId, label: "SLA Deadline",
        expression: 'value ? new Date(value).toLocaleString() : "—"' },
      { id: "slaTimeRemaining" as ColumnId, label: "Time Remaining",
        expression: `
          if (!value) return "—";
          const hours = parseFloat(value);
          if (hours < 0) return "🔴 OVERDUE (" + Math.abs(hours).toFixed(1) + "h)";
          if (hours < 2) return "🟠 " + hours.toFixed(1) + "h";
          if (hours < 6) return "🟡 " + hours.toFixed(1) + "h";
          return "🟢 " + hours.toFixed(1) + "h";
        ` },
      { id: "escalationStatus" as ColumnId, label: "Escalation",
        expression: 'value || "—"' },
      { id: "regulatorySubmissionStatus" as ColumnId, label: "IND Status",
        expression: 'value || "—"' }
    ],
    lookup: lookup("adverse-events", sortBy("slaDeadline", "ASC"))
  }),

  markdown(`---

## Past Similar Cases (CBR Analysis)

Similar historical adverse events identified by the Case-Based Reasoning system.

_Note: Currently showing precedents for a demo AE. Full row-selection UX pending DSL enhancements._`),

  // Precedents table
  table({
    sortable: true,
    pageSize: 10,
    columns: [
      { id: "score" as ColumnId, label: "Similarity",
        expression: '(value * 100).toFixed(1) + "%"' },
      { id: "grade" as ColumnId, label: "Grade" },
      { id: "eventType" as ColumnId, label: "Event Type",
        expression: 'value ? value.replace(/_/g, " ").toLowerCase().replace(/\\b\\w/g, l => l.toUpperCase()) : "—"' },
      { id: "outcome" as ColumnId, label: "Outcome" },
      { id: "resolutionTime" as ColumnId, label: "Resolution (hrs)",
        expression: 'value !== null ? value.toFixed(1) : "—"' },
      { id: "reportedAt" as ColumnId, label: "Reported",
        expression: 'new Date(value).toLocaleString()' }
    ],
    lookup: lookup("ae-precedents", sortBy("score", "DESC"))
  }),

  { datasets: [adverseEventsDs, aePrecedentsDs] }
);
