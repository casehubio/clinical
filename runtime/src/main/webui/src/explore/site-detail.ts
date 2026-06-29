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
    lookup: lookup("patients", groupBy("siteId", col("siteId")))
  }),

  // Site-level metrics
  columns(
    [4, 4, 4],
    [metric({
      title: "Total Patients",
      lookup: lookup("patients", groupBy(null, count("id")))
    })],
    [metric({
      title: "Active Patients",
      lookup: lookup(
        "patients",
        filterBy("enrollmentStatus", "EQUALS_TO", "ELIGIBLE"),
        groupBy(null, count("id"))
      )
    })],
    [metric({
      title: "Candidates",
      lookup: lookup(
        "patients",
        filterBy("enrollmentStatus", "EQUALS_TO", "CANDIDATE"),
        groupBy(null, count("id"))
      )
    })]
  ),

  // Patients table
  table({
    sortable: true,
    pageSize: 25,
    listening: true,
    columns: [
      { id: "siteId" as ColumnId, label: "Site",
        expression: 'value.substring(0, 8) + "..."' },
      { id: "patientId" as ColumnId, label: "Patient ID",
        expression: 'value.substring(0, 12) + "..."' },
      { id: "enrollmentStatus" as ColumnId, label: "Status" },
      { id: "screeningResult" as ColumnId, label: "Screening",
        expression: 'value === "CRITERIA_MET" ? "✅ CRITERIA MET" : value || "—"' },
      { id: "consentStatus" as ColumnId, label: "Consent",
        expression: 'value === "CRITERIA_MET" ? "✅" : value || "—"' }
    ],
    lookup: lookup("patients")
  }),
  { datasets: [patientsDs] }
);
