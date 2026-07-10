import { rows, markdown, html, table, lookup } from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import { workItemsDs } from "../datasets.js";

export function workQueue(): Component {
  return rows(
    markdown("## Work Queue\n\nPending tasks across all clinical workflows."),
    table({
      title: "Work Items",
      lookup: lookup(workItemsDs.id),
      sortable: true,
      pageSize: 25,
      columns: [
        { id: "title" as never, name: "Title" },
        { id: "priority" as never, name: "Priority" },
        { id: "status" as never, name: "Status" },
        { id: "category" as never, name: "Category" },
        { id: "slaStatus" as never, name: "SLA Status" },
      ],
      emptyMessage: "No pending tasks. All items are up to date.",
    }),
  );
}

export const workQueueDatasets = [workItemsDs];
