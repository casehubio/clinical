import { dataset } from "@casehubio/pages-ui";

export const TRIAL_ID = "316e3846-4ea7-3b18-a6f7-e01ce6582a69";

export const trialSummaryDs = dataset("trial-summary", `/api/trials/${TRIAL_ID}/summary`);
export const agentsDs = dataset("agents", `/api/trials/${TRIAL_ID}/agents`);
export const patientsDs = dataset("patients", `/api/trials/${TRIAL_ID}/patients`);
export const adverseEventsDs = dataset("adverse-events", `/api/trials/${TRIAL_ID}/adverse-events`);
export const deviationsDs = dataset("deviations", `/api/trials/${TRIAL_ID}/deviations`);
export const ledgerEntriesDs = dataset("ledger-entries", `/api/trials/${TRIAL_ID}/ledger-entries`);
