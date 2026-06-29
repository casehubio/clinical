import { page, columns, metric, table, markdown, selector, lookup, groupBy, col, count, filterBy } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { patientsDs } from "../datasets";

/**
 * Explore Mode: Site Detail
 *
 * Per-site patient overview with cross-filtering capability.
 *
 * Components:
 * - Site selector dropdown
 * - Site-level metrics (enrolled, active, completed)
 * - Patients table with site-based filtering
 *
 * Cross-filtering: when a site is selected in the dropdown, the patients table
 * filters to show only that site's patients.
 */
export const siteDetail = page("Site Detail",
  markdown(`## Site-Level Patient Overview

Drill down into enrollment and patient status at each trial site. Select a site from the dropdown to filter the patient list.`),

  // Site selector
  selector({
    subtype: "dropdown",
    selfApply: true,
    notification: true,
    lookup: lookup(patientsDs, [], [groupBy(["siteName"], [col("siteName")])])
  }),

  // Site-level metrics
  columns(
    { span: 4 },
    metric({
      title: "Patients Enrolled",
      lookup: lookup(patientsDs, [], [groupBy([], [count("enrollmentId")])])
    }),
    { span: 4 },
    metric({
      title: "Active Patients",
      lookup: lookup(
        patientsDs,
        [filterBy("consentStatus", "EQUALS_TO", "CONSENTED")],
        [groupBy([], [count("enrollmentId")])]
      )
    }),
    { span: 4 },
    metric({
      title: "Withdrawn",
      lookup: lookup(
        patientsDs,
        [filterBy("consentStatus", "EQUALS_TO", "WITHDRAWN")],
        [groupBy([], [count("enrollmentId")])]
      )
    })
  ),

  // Patients table with listening enabled for cross-filtering
  table({
    sortable: true,
    pageSize: 25,
    listening: true,
    columns: [
      { id: "siteName" as ColumnId, label: "Site" },
      { id: "patientId" as ColumnId, label: "Patient ID",
        expression: 'value.substring(0, 8) + "..."' },
      { id: "enrollmentDate" as ColumnId, label: "Enrolled",
        expression: 'new Date(value).toLocaleDateString()' },
      { id: "consentStatus" as ColumnId, label: "Consent Status",
        expression: `
          if (value === "CONSENTED") return "✅ CONSENTED";
          if (value === "WITHDRAWN") return "❌ WITHDRAWN";
          if (value === "PENDING") return "⏳ PENDING";
          return value || "—";
        ` },
      { id: "screeningStatus" as ColumnId, label: "Screening",
        expression: `
          if (value === "CRITERIA_MET") return "✅ CRITERIA MET";
          if (value === "EXCLUDED") return "❌ EXCLUDED";
          if (value === "PENDING") return "⏳ PENDING";
          return value || "—";
        ` },
      { id: "activeAeCount" as ColumnId, label: "Active AEs" },
      { id: "totalDeviationCount" as ColumnId, label: "Deviations" }
    ],
    lookup: lookup(patientsDs, [], [])
  })
);
