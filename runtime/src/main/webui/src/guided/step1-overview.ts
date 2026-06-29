import { page, columns, metric, barChart, table, markdown, lookup, groupBy, col, sum } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { TRIAL_ID, trialSummaryDs, sitesDs } from "../datasets";
import { STEP1_NARRATIVE } from "../narrative";

export const step1Overview = page("1. Trial Overview",
  markdown(`## ONCO-2024-001 — Phase III Oncology Trial\n\n${STEP1_NARRATIVE}`),

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

  barChart({
    title: "Enrollment by Site",
    lookup: lookup("sites", groupBy("investigatorId", col("investigatorId"), sum("enrolledCount")))
  }),

  table({
    sortable: true,
    columns: [
      { id: "investigatorId" as ColumnId, label: "Investigator" },
      { id: "status" as ColumnId, label: "Status",
        expression: 'value === "ACTIVE" ? "✅ ACTIVE" : value === "PENDING" ? "⏳ PENDING" : value' },
      { id: "enrolledCount" as ColumnId, label: "Enrolled" },
      { id: "adverseEventCount" as ColumnId, label: "Adverse Events" },
      { id: "deviationCount" as ColumnId, label: "Deviations" }
    ],
    lookup: lookup("sites")
  }),
  { datasets: [trialSummaryDs, sitesDs] }
);
