import { page, table, markdown, lookup, sortBy } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { adverseEventsDs } from "../datasets";

/**
 * Explore Mode: Adverse Events
 *
 * All adverse events across the trial with full detail columns.
 *
 * Components:
 * - AE table with grade, type, site, patient, timestamps, SLA status, escalation status
 * - Sortable by all columns
 * - SLA styling: overdue events show red emoji prefix
 */
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
      { id: "grade" as ColumnId, label: "Grade",
        expression: 'value' },
      { id: "eventType" as ColumnId, label: "Event Type",
        expression: 'value.replace(/_/g, " ").toLowerCase().replace(/\\b\\w/g, l => l.toUpperCase())' },
      { id: "siteName" as ColumnId, label: "Site" },
      { id: "patientId" as ColumnId, label: "Patient",
        expression: 'value.substring(0, 8) + "..."' },
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
  { datasets: [adverseEventsDs] }
);
