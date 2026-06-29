import { page, columns, metric, table, markdown, lookup, groupBy, col, sortBy } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { trialSummaryDs, ledgerEntriesDs } from "../datasets";

/**
 * Explore Mode: Trial Dashboard
 *
 * High-level trial overview with metrics, enrollment chart, and recent activity.
 *
 * Components:
 * - 4-column metrics row: phase, enrollment, AE count, deviation count
 * - Enrollment bar chart by site
 * - Recent activity table (last 10 ledger entries)
 */
export const trialDashboard = page("Trial Dashboard",
  markdown(`## ONCO-2024-001 Trial Dashboard

Real-time overview of trial status across all sites. This page aggregates enrollment, safety events, and protocol compliance metrics.`),

  // 4-column metrics row
  columns(
    [3, 3, 3, 3],
    [metric({
      title: "Trial Phase",
      lookup: lookup("trial-summary", groupBy(null, col("phase")))
    })],
    [metric({
      title: "Total Enrolled",
      lookup: lookup("trial-summary", groupBy(null, col("totalEnrolled")))
    })],
    [metric({
      title: "Adverse Events",
      lookup: lookup("trial-summary", groupBy(null, col("totalAdverseEvents")))
    })],
    [metric({
      title: "Protocol Deviations",
      lookup: lookup("trial-summary", groupBy(null, col("totalDeviations")))
    })]
  ),

  // Recent activity table (last 10 ledger entries)
  table({
    sortable: true,
    pageSize: 10,
    columns: [
      { id: "occurredAt" as ColumnId, label: "Timestamp",
        expression: 'new Date(value).toLocaleString()' },
      { id: "entryType" as ColumnId, label: "Event Type",
        expression: 'value.replace("LedgerEntry", "").replace(/([A-Z])/g, " $1").trim()' },
      { id: "actorId" as ColumnId, label: "Actor",
        expression: 'value.substring(0, 24) + (value.length > 24 ? "..." : "")' },
      { id: "subjectId" as ColumnId, label: "Subject",
        expression: 'value.substring(0, 8) + "..."' }
    ],
    lookup: lookup("ledger-entries", sortBy("occurredAt", "DESC"))
  }),
  { datasets: [trialSummaryDs, ledgerEntriesDs] }
);
