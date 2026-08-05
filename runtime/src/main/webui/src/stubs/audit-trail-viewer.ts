import { dataTable, lookup } from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";

export function auditTrailStub(datasetId: string): Component {
  return dataTable({
    title: "Audit Trail",
    lookup: lookup(datasetId),
    sortable: true,
    pageSize: 25,
    columns: [
      { id: "occurredAt" as never, name: "Timestamp" },
      { id: "entryType" as never, name: "Type" },
      { id: "actorId" as never, name: "Actor" },
      { id: "subjectId" as never, name: "Subject" },
      { id: "digest" as never, name: "Digest", expression: 'value ? value.substring(0, 16) + "..." : ""' },
    ],
  });
}
