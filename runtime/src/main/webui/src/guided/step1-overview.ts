import { page, columns, metric, barChart, table, markdown, lookup, groupBy, col, sum } from "@casehubio/pages-ui";
import { TRIAL_ID, trialSummaryDs } from "../datasets";
import { STEP1_NARRATIVE } from "../narrative";

/**
 * Step 1: Trial Overview
 *
 * Components:
 * - Narrative markdown
 * - 4-column metric row: phase, enrolled, AEs, deviations
 * - Enrollment bar chart by site
 * - Sites table with status, investigator, enrolled count, AE count
 */
export const step1Overview = page("1. Trial Overview",
  markdown(`## ONCO-2024-001 — Phase III Oncology Trial\n\n${STEP1_NARRATIVE}`),

  // 4-column metrics row: trial phase, total enrolled, active AEs, protocol deviations
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

  // TODO: Add enrollment bar chart by site when sites dataset is available
  // barChart({
  //   title: "Enrollment by Site",
  //   lookup: lookup("sites", groupBy("siteName", col("siteName"), sum("enrolled")))
  // }),

  // TODO: Add sites table when sites dataset is available
  // table({
  //   sortable: true,
  //   columns: [
  //     { id: "siteName" as ColumnId },
  //     { id: "investigator" as ColumnId },
  //     { id: "enrolled" as ColumnId },
  //     { id: "status" as ColumnId, expression: 'value === "ACTIVE" ? "✅ ACTIVE" : value' }
  //   ],
  //   lookup: lookup("sites", [], [])
  // })
  { datasets: [trialSummaryDs] }
);
