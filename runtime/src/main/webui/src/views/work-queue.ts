import { rows, html } from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import { DEMO_MODE } from "../datasets.js";

export function workQueue(): Component {
  return rows(
    html(`<work-item-inbox
      ${DEMO_MODE ? "" : 'endpoint="/api/workitems?candidateGroups=clinical"'}
    ></work-item-inbox>`),
  );
}

export const workQueueDatasets: never[] = [];
