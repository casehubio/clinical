import {
  rows, columns, tabs, table, metric, barChart, pieChart, html,
  lookup, groupBy, filterBy, col, count, sum,
} from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import {
  trialSummaryDs, sitesDs, agentsDs, ledgerEntriesDs,
  patientsDs, workItemsDs, TRIAL_ID,
} from "../datasets.js";

export function operations(): Component {
  const trialDashboard = rows(
    columns([3, 3, 3, 3],
      [metric({ title: "Trial Phase", lookup: lookup(trialSummaryDs.uuid, groupBy(null, col("phase"))) })],
      [metric({ title: "Total Enrolled", lookup: lookup(trialSummaryDs.uuid, groupBy(null, sum("totalEnrolled"))) })],
      [metric({ title: "Adverse Events", lookup: lookup(trialSummaryDs.uuid, groupBy(null, sum("activeAeCount"))) })],
      [metric({ title: "Protocol Deviations", lookup: lookup(trialSummaryDs.uuid, groupBy(null, sum("deviationCount"))) })],
    ),
    barChart({
      title: "Enrollment by Site: Target vs Actual",
      lookup: lookup(sitesDs.uuid, groupBy("investigatorId", col("investigatorId"), col("targetEnrollment"), col("enrolledCount"))),
    }),
    table({
      title: "Recent Activity",
      lookup: lookup(ledgerEntriesDs.uuid),
      sortable: true,
      pageSize: 10,
      columns: [
        { id: "timestamp" as never, name: "Time", expression: 'value ? new Date(value).toLocaleString() : ""' },
        { id: "entryType" as never, name: "Event Type", expression: 'value ? value.replace(/([A-Z])/g, " $1").trim() : ""' },
        { id: "actorId" as never, name: "Actor", expression: 'value ? value.substring(0, 12) + "..." : ""' },
        { id: "subjectId" as never, name: "Subject", expression: 'value ? value.substring(0, 8) + "..." : ""' },
      ],
    }),
  );

  const trustGovernance = rows(
    table({
      title: "Agent Trust Scores",
      lookup: lookup(agentsDs.uuid),
      sortable: true,
      pageSize: 25,
      columns: [
        { id: "capability" as never, name: "Capability" },
        { id: "trustScore" as never, name: "Trust Score", expression: 'value >= 0.8 ? "🟢 " + Number(value).toFixed(3) : value >= 0.6 ? "🟡 " + Number(value).toFixed(3) : value >= 0.4 ? "🟠 " + Number(value).toFixed(3) : "🔴 " + Number(value).toFixed(3)' },
        { id: "trustDimension" as never, name: "Dimension" },
        { id: "maturityPhase" as never, name: "Maturity", expression: 'value === 0 ? "🔵 Bootstrap" : value === 1 ? "🟡 Emerging" : "🟢 Established"' },
        { id: "decisionCount" as never, name: "Decisions" },
        { id: "endorsementRatio" as never, name: "Endorsement", expression: 'value != null ? (Number(value) * 100).toFixed(1) + "%" : "—"' },
      ],
    }),
  );

  const slaHealth = rows(
    pieChart({
      title: "Work Items by SLA Status",
      lookup: lookup(workItemsDs.uuid, groupBy("slaStatus", col("slaStatus"), count("id"))),
    }),
  );

  const compliance = rows(
    html(`<regulatory-compliance-summary></regulatory-compliance-summary>`),
  );

  const gdpr = rows(
    html(`<gdpr-erasure-action
      endpoint="/api/gdpr/erasure/patients"
      subject-label="Patient"
    ></gdpr-erasure-action>`),
  );

  return tabs(
    ["Trial Dashboard", trialDashboard],
    ["Trust & Governance", trustGovernance],
    ["SLA Health", slaHealth],
    ["Compliance", compliance],
    ["GDPR", gdpr],
  );
}

export const operationsDatasets = [
  trialSummaryDs, sitesDs, agentsDs, ledgerEntriesDs, patientsDs, workItemsDs,
];
