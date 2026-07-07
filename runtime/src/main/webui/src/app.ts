import { page, tree } from "@casehubio/pages-ui";
import { workQueue, workQueueDatasets } from "./views/work-queue.js";
import {
  safetyWorkbench,
  safetyWorkbenchDatasets,
} from "./views/safety-workbench.js";
import {
  protocolWorkbench,
  protocolWorkbenchDatasets,
} from "./views/protocol-workbench.js";
import { operations, operationsDatasets } from "./views/operations.js";

export const app = page(
  "CaseHub Clinical",
  tree(
    ["Work Queue", workQueue()],
    ["Safety Workbench", safetyWorkbench()],
    ["Protocol Workbench", protocolWorkbench()],
    ["Operations", operations()],
  ),
  {
    datasets: [
      ...workQueueDatasets,
      ...safetyWorkbenchDatasets,
      ...protocolWorkbenchDatasets,
      ...operationsDatasets,
    ],
  },
);
