import { rows, markdown, html } from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import { workItemsDs } from "../datasets.js";

export function workQueue(): Component {
  return rows(
    markdown("## Work Queue\n\nPending tasks across all clinical workflows."),
    html(`<work-item-inbox
      endpoint="/api/workitems"
      mode="my-work"
    ></work-item-inbox>`),
  );
}

export const workQueueDatasets = [workItemsDs];
