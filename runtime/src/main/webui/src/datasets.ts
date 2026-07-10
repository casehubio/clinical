import { bind } from "@casehubio/pages-ui";
import { csvSource } from "@casehubio/pages-data";
import type { DataSourceBinding } from "@casehubio/pages-data";
import type { DataSetId } from "@casehubio/pages-data";

import adverseEventsCsv from "./mock/adverse-events.csv?raw";
import deviationsCsv from "./mock/deviations.csv?raw";
import trialSummaryCsv from "./mock/trial-summary.csv?raw";
import sitesCsv from "./mock/sites.csv?raw";
import agentsCsv from "./mock/agents.csv?raw";
import ledgerEntriesCsv from "./mock/ledger-entries.csv?raw";
import patientsCsv from "./mock/patients.csv?raw";
import aePrecedentsCsv from "./mock/ae-precedents.csv?raw";
import deviationPrecedentsCsv from "./mock/deviation-precedents.csv?raw";
import workItemsCsv from "./mock/work-items.csv?raw";

export const DEMO_MODE = import.meta.env.VITE_DEMO_MODE === "true";

const DEMO_TRIAL_ID = "316e3846-4ea7-3b18-a6f7-e01ce6582a69";

export const TRIAL_ID = DEMO_MODE
  ? DEMO_TRIAL_ID
  : import.meta.env.VITE_TRIAL_ID || DEMO_TRIAL_ID;

export function dualDataset(
  id: string,
  endpoint: string,
  mockCsv: string,
): DataSourceBinding {
  if (DEMO_MODE) {
    return bind(id, csvSource(mockCsv));
  }
  return {
    id: id as DataSetId,
    source: { connect() {}, disconnect() {} },
    url: endpoint,
  } as DataSourceBinding;
}

export const adverseEventsDs = dualDataset(
  "adverse-events",
  `/api/trials/${TRIAL_ID}/adverse-events`,
  adverseEventsCsv,
);

export const deviationsDs = dualDataset(
  "deviations",
  `/api/trials/${TRIAL_ID}/deviations`,
  deviationsCsv,
);

export const trialSummaryDs = dualDataset(
  "trial-summary",
  `/api/trials/${TRIAL_ID}/summary`,
  trialSummaryCsv,
);

export const sitesDs = dualDataset(
  "sites",
  `/api/trials/${TRIAL_ID}/sites`,
  sitesCsv,
);

export const agentsDs = dualDataset(
  "agents",
  `/api/trials/${TRIAL_ID}/agents`,
  agentsCsv,
);

export const ledgerEntriesDs = dualDataset(
  "ledger-entries",
  `/api/trials/${TRIAL_ID}/ledger-entries`,
  ledgerEntriesCsv,
);

export const patientsDs = dualDataset(
  "patients",
  `/api/trials/${TRIAL_ID}/patients`,
  patientsCsv,
);

export const workItemsDs = dualDataset(
  "work-items",
  `/api/workitems?candidateGroups=clinical`,
  workItemsCsv,
);

export const aePrecedentsDs = dualDataset(
  "ae-precedents",
  `/api/trials/${TRIAL_ID}/adverse-events/ae-demo-001/precedents`,
  aePrecedentsCsv,
);

export const deviationPrecedentsDs = dualDataset(
  "deviation-precedents",
  `/api/trials/${TRIAL_ID}/deviations/dev-demo-001/precedents`,
  deviationPrecedentsCsv,
);

export const allDatasets = [
  adverseEventsDs, deviationsDs, trialSummaryDs, sitesDs,
  agentsDs, ledgerEntriesDs, patientsDs, workItemsDs,
  aePrecedentsDs, deviationPrecedentsDs,
];
