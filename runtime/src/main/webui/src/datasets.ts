import { dataset } from "@casehubio/pages-ui";

export const TRIAL_ID = "316e3846-4ea7-3b18-a6f7-e01ce6582a69";

export const trialSummaryDs = dataset("trial-summary", `/trials/${TRIAL_ID}/summary`, {
  expression: "[$]"
});
export const sitesDs = dataset("sites", `/trials/${TRIAL_ID}/sites`);
export const agentsDs = dataset("agents", `/trials/${TRIAL_ID}/agents`);
export const patientsDs = dataset("patients", `/trials/${TRIAL_ID}/patients`);
export const adverseEventsDs = dataset("adverse-events", `/trials/${TRIAL_ID}/adverse-events`);
export const deviationsDs = dataset("deviations", `/trials/${TRIAL_ID}/deviations`);
export const ledgerEntriesDs = dataset("ledger-entries", `/trials/${TRIAL_ID}/ledger-entries`);
