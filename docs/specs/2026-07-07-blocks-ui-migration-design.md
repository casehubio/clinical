# Clinical Product UI — blocks-ui Migration Design Spec

**Date:** 2026-07-07
**Parent epic:** casehubio/blocks-ui#35 (cross-repo component migration tracking)
**Child epic:** casehubio/blocks-ui#38 (clinical migration plan)
**Supersedes:** `specs/2026-06-27-clinical-demo-ui-design.md` (guided demo UI — retained for reference)

## Purpose

Rebuild the clinical trial UI as a functioning product using blocks-ui shared components, following AML's pattern. The current UI is a guided demo walkthrough — it tells a story but is not a working product. This migration produces an operational tool that clinical staff can use, with the guided narrative deferred to a separate concern (different entry point, overlay, or second window — TBD later).

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Product vs demo | Product first | The guided walkthrough merged demo and product concerns. Build what works, then layer guidance on top. |
| Navigation | Sidebar with 4 views | Matches AML's pattern. Each view is a self-contained operational tool. |
| Workbench count | Two (Safety + Protocol) | AE and deviation lifecycles have different actors, approval mechanisms, SLA bases, and regulatory drivers. One workbench per workflow type. |
| Workbench component | Manual split-pane now, `<case-workbench>` when AML promotes | Design to the same shape. Tab registrations and detail components stay unchanged when the container swaps. |
| Data strategy | Dual-mode — inline mocks + live endpoints | Demo without a backend (clone, run, see it). Production with real data. Build-time flag switches modes. |
| Theme | `@casehubio/pages-ui-tokens` | Drop custom `theme.ts`. Shared tokens across all harnesses. |
| Trial resolution | Single-trial deployment, env-configured | One clinical instance per trial. `VITE_TRIAL_ID` env var in production; hardcoded demo UUID in mock mode. |
| Routing | `pages-runtime` `tree()` sidebar | URL hash navigation via `loadSite()`. Same mechanism as AML. Deep linking to entities deferred (needs casehub-pages enhancement). |
| Guided mode | Deferred | Separate concern — not part of this spec. |

---

## Navigation & View Structure

```
Sidebar:
  Work Queue
  Safety Workbench
  Protocol Workbench
  Operations
```

### Routing & Navigation

The app uses `pages-runtime`'s `loadSite()` with a `tree()` component that renders a sidebar with four top-level navigation entries. `loadSite()` builds a page path map and handles hash-based URL navigation — each sidebar entry maps to a URL fragment (e.g., `#/work-queue`, `#/safety-workbench`). Browser back/forward works across view switches.

```typescript
export const app = page("CaseHub Clinical",
  tree(
    ["Work Queue", workQueue],
    ["Safety Workbench", safetyWorkbench],
    ["Protocol Workbench", protocolWorkbench],
    ["Operations", operations],
  )
);
```

Default route: Work Queue (first entry in the tree).

**Deep linking to entities:** Not supported in v1. `pages-runtime`'s `tree()` navigates between views, not to specific rows within a view. Deep linking to a specific AE or deviation would require query-parameter parsing on top of the tree mechanism — deferred until the workbench UX validates the interaction model.

### Work Queue

`<work-item-inbox>` showing clinical work items. Clinical staff see clinical items because they belong to clinical groups (`SPONSOR`, `INVESTIGATOR`, `COORDINATOR`, `MONITOR`) in the identity system — this is not a component property but an identity configuration. The inbox's `identity` property receives the user's `WorkIdentity` (userId, displayName, groups); filtering is server-side by group membership and client-side by the component's tab logic (`my-work`, `claimable`, `all`).

Shows pending AE reviews, PI authorisation requests, IRB consultations, IND filing deadlines. Clicking a work item navigates to the relevant workbench with that item selected. Entry point for "what do I need to do right now."

**Navigation to workbenches:** When a work item is clicked, the `work-item-inbox` emits a `pages-event`. The app handles this by:

1. **Type discrimination** — the work item's `types` array carries the domain classification. `types.includes("adverse-event")` routes to Safety Workbench, `types.includes("deviation-review")` routes to Protocol Workbench. This field is set by clinical code: `AdverseEventService` uses `.types(List.of("adverse-event"))` for directly-created items; engine-created items (Grade 3+ AE escalation gates, IRB consultations) set `types` via the YAML `humanTask` binding's `types` property.
2. **Entity resolution** — the work item's `payload` (JSON) contains the entity reference. Clinical's `AdverseEventService` includes the AE ID in the payload via `payload(ae)`. Engine-created work items carry the case context in their payload, which includes the originating entity ID.
3. **Selection handoff** — the app programmatically navigates to the target workbench view via `tree()` navigation, then sets the selected entity ID (extracted from payload) on the workbench's data table. The workbench listens for a selection property and scrolls/highlights the matching row.

Note: `callerRef` is NOT used for UI navigation — it is reserved for platform-level case/gate resolution (e.g., `IrbDecisionListener` uses `CallerRef.parse()` to extract the case ID). The `WorkItemResponse` TypeScript type currently omits `types` (schema mismatch with the backend, which does return it) — this must be fixed in blocks-ui-core (blocks-ui#42).

### Safety Workbench

Split-pane. Left: AE list (`<pages-data-table>`, later `<case-list-pane>`). Right: tabbed detail for selected AE.

**List columns:** Grade (colour-coded), Event Type, Patient (truncated), Site, SLA Remaining (`<sla-indicator>`), Escalation Status, IND Status.

**Row styling:** Grade 4/5 rows highlighted. Overdue SLA rows red.

**Detail tabs:**

| Tab | Components | Data source |
|-----|-----------|-------------|
| Overview | Grade badge, event type, patient, site, reported time, SLA deadline (`<sla-indicator>`), escalation status | `adverse-events` dataset (selected row) |
| SUSAR Evaluation | Criteria breakdown (unexpected + suspected + serious), evaluator agent, trust score at routing, gate status. `<approval-gate>` for pending decisions. | AE SUSAR fields + governance endpoint |
| Trust & Attestation | `<trust-feedback-display>` — score before/after, dimension, attestation verdict | Gate approval response |
| Regulatory | IND submission status, `<sla-breach-policy-indicator>` with clinical two-tier policy, filing deadline | AE regulatory fields |
| Precedents | `<cbr-precedents-panel>` — similar past AEs with similarity %, outcome, resolution time | `ae-precedents` dataset |
| Audit Trail | Scoped `<audit-trail-viewer>` (stub with DSL `table()` until blocks-ui#9) — ledger entries for this AE only | `ledger-entries` dataset filtered by subject ID |

### Protocol Workbench

Split-pane. Left: deviation list (`<pages-data-table>`, later `<case-list-pane>`). Right: tabbed detail for selected deviation.

**List columns:** Deviation Type, Severity (colour-coded), Site, PI Approval Status (emoji), IRB Decision (emoji), Reported.

**Row styling:** CRITICAL severity rows highlighted. Expired PI deadlines red (`piApprovalStatus === "EXPIRED"`).

**Detail tabs:**

| Tab | Components | Data source |
|-----|-----------|-------------|
| Overview | Type, severity, site, PI, reported time, approval status | `deviations` dataset (selected row) |
| PI Commitment | `<commitment-lifecycle>` — COMMANDED → APPROVED/DECLINED → ESCALATED timeline, channel messages, deadline | Commitment endpoint parameterised by deviation |
| IRB Review | `<approval-gate>` for IRB decision, committee ID, 72h deadline (`<sla-indicator>`) | Deviation IRB fields |
| Precedents | `<cbr-precedents-panel>` — similar past deviations | `deviation-precedents` dataset |
| Audit Trail | Scoped `<audit-trail-viewer>` (stub until #9) — ledger entries for this deviation | `ledger-entries` dataset filtered by subject ID |

### Operations

Tabbed dashboard (no workbench split-pane).

| Tab | Components | Data source |
|-----|-----------|-------------|
| Trial Dashboard | `<kpi-metric-row>` (enrollment, sites, AE count, deviation count), enrollment bar chart by site, recent activity table | `trial-summary`, `sites`, `ledger-entries` datasets |
| Trust & Governance | `<trust-score-panel>` (stub with DSL `table()` until blocks-ui#11) per agent, gate activity table, maturity phase breakdown | `agents` dataset |
| SLA Health | Work items by SLA status (pie/donut chart), overdue list, breach history | `work-items` dataset |
| Compliance | `<regulatory-compliance-summary>` — FDA/GCP/GDPR requirements grid | Static data (regulation definitions) |
| GDPR | `<gdpr-erasure-action>` — consent withdrawal with receipt | Erasure endpoint |

---

## Why Two Workbenches (Not One)

AML uses a single "Investigations" workbench because all cases follow the same lifecycle with the same actors and same detail tabs. Clinical has two fundamentally different event workflows:

| | Adverse Events | Protocol Deviations |
|---|---|---|
| Lifecycle | REPORTED → ESCALATED → SUSAR gate → APPROVED → IND filing | REPORTED → PI COMMANDED → APPROVED/DECLINED → IRB → RESOLVED |
| Primary actor | Investigator (gate approver) | Principal Investigator (commanded party) |
| Secondary actor | SUSAR evaluator agent (trust-routed) | IRB committee (ethics review) |
| Approval mechanism | WorkItem-based oversight gate | qhorus COMMAND → Commitment lifecycle |
| Regulatory driver | ICH E6(R3) §5.17, 21 CFR 312.32 | ICH E6(R3) §5.20 |
| SLA basis | Grade-based (1h / 24h / 7d) | Severity-based (PI deadline, IRB 72h) |
| Detail tabs | SUSAR criteria, trust routing, attestation, IND status | PI commitment lifecycle, channel messages, IRB decision |
| List columns | Grade, event type, SLA remaining, escalation, IND status | Type, severity, PI approval, IRB decision |

A single workbench would need type-conditional columns, conditional tab visibility, and branching row styling — all complexity that two workbenches eliminate. The `<case-workbench>` component validates this: it's designed to be configurable and instantiated multiple times with different configurations.

---

## blocks-ui Component Consumption

### Production-ready (no blockers)

| Component | Where used | Configuration |
|---|---|---|
| `<work-item-inbox>` | Work Queue | `identity` with clinical group memberships; `endpoint` pointing to work-items API |
| `<pages-data-table>` | Both workbench list panes | Per-workbench column definitions and row styling |
| `<approval-gate>` | SUSAR Evaluation tab, IRB Review tab | Configurable endpoint + request body. Post-action display for trust score delta (SUSAR) and IRB decision. |
| `<sla-indicator>` | Safety Overview tab, Work Queue | Deadline from AE `slaDeadline` field |
| `<kpi-metric-row>` | Operations Trial Dashboard | Static data mode (from dataset lookup) |

### Stubbed until blocks-ui builds them

| Component | Stub with | blocks-ui issue | Stub provenance |
|---|---|---|---|
| `<audit-trail-viewer>` | DSL `table()` | #9 | Clinical-local (no blocks-ui stub package exists). Merkle verify button is part of the full blocks-ui#9 component, not the stub. |
| `<trust-score-panel>` | DSL `table()` with trust score columns | #11 | blocks-ui stub package (exports `COMPONENT_NAME` only) |
| `<case-timeline>` | Omit initially | #10 | blocks-ui stub package (exports `COMPONENT_NAME` only) |

**Note:** `<audit-trail-viewer>` has no package in blocks-ui at all, unlike the other two. Clinical's stub is entirely local code. When blocks-ui#9 ships, clinical swaps from its local `table()` stub to the blocks-ui import. Import paths differ from the other two components — clinical imports from `./stubs/audit-trail-viewer` (local) rather than `@casehubio/blocks-ui-audit-trail-viewer`.

### Blocked on AML promotion

| Component | Impact |
|---|---|
| `<case-workbench>` | Replace manual split-pane in both workbenches. View structure unchanged — only the container swaps. |

---

## Promotion Candidates — Components Clinical Builds for blocks-ui

Six components built as Lit web components in clinical's `webui/src/components/`, using `pages-ui-tokens` for styling and `emitPagesEvent()` for communication. Domain-specific bits are configurable properties.

### `<commitment-lifecycle>`

Timeline visualisation of qhorus COMMAND → RESPONSE → DONE/DECLINE lifecycle.

**Properties:**
- `commitmentId: string` — fetches lifecycle state from endpoint
- `endpoint: string` — REST URL pattern (default `/api/commitments/{id}`)
- `stages: StageDefinition[]` — override default stage labels (e.g., clinical: "PI Commanded", AML: "Officer Assigned")

**Renders:** Horizontal step indicator. Each node shows actor, timestamp, and status. Active node pulses. Expired/declined nodes red. Completed nodes green. Channel messages listed below.

**Generalises because:** qhorus commitments are platform-level. AML needs this for compliance officer sign-off. Any harness with HITL gates creates commitments.

### `<cbr-precedents-panel>`

Table of similar past cases from CaseMemoryStore with similarity scores.

**Properties:**
- `endpoint: string` — REST URL for precedent lookup
- `columns: ColumnDef[]` — override which fields display (default: similarity, grade/severity, outcome, resolution time, reported date)
- `emptyMessage: string` — text when no precedents found

**Renders:** Custom table (styled with `pages-ui-tokens`) with similarity column as percentage bar, outcome with status badge, resolution time in human-readable duration. Row click emits `pages-event` with precedent ID. Note: `<pages-data-table>` is a DSL function for pages-runtime's rendering pipeline, not a web component — Lit components cannot embed it in their shadow DOM.

**Generalises because:** CaseMemoryStore returns the same shape regardless of domain. AML would show similar past investigations identically.

### `<trust-feedback-display>`

Post-gate decision summary showing trust score impact.

**Properties:**
- `gateDecision: object` — response from gate approval endpoint (decision, investigator, attestation, trust scores)
- Alternatively: `endpoint: string` + `gateId: string` to fetch

**Renders:** Card with five rows: gate decision (APPROVED/REJECTED badge), approver identity, attestation verdict (ENDORSED/CHALLENGED), trust score delta (before → after with arrow and colour), dimension affected. Compact mode collapses to single line.

**Generalises because:** Every harness with trust-weighted agents shows this after gate decisions. Shape is identical — only dimension labels change.

### `<regulatory-compliance-summary>`

Grid of regulatory requirements and satisfaction status.

**Properties:**
- `requirements: RequirementDefinition[]` — array of `{ regulation, requirement, mechanism, status, evidenceUrl? }`
- Or `endpoint: string` to fetch from REST

**Renders:** `<pages-data-table>` with columns: Regulation, Requirement, Mechanism, Status (MET/PARTIAL/GAP/BREACHED with colour badges), Evidence (link). Row styling by status.

**Generalises because:** Clinical: FDA/GCP/GDPR. AML: FinCEN/FATF/GDPR. Identical shape — domain provides rows, component renders.

### `<gdpr-erasure-action>`

Consent withdrawal / data erasure workflow.

**Properties:**
- `endpoint: string` — REST URL for erasure
- `subjectLabel: string` — "Patient" for clinical, "Subject" for AML
- `reasonOptions: string[]` — erasure reason enum values

**Renders:** Subject ID input, reason dropdown, confirmation screen before destructive action, receipt display after success (erasure ID, timestamp, entry count erased, ALREADY_WITHDRAWN handling). Confirmation uses an in-component confirmation panel (state-based) rather than `<blocks-confirm-dialog>` — avoids a blocks-ui-core dependency in a promotion candidate component. When promoted to blocks-ui, the confirmation can optionally migrate to `<blocks-confirm-dialog>`.

**Generalises because:** AML has the same erasure flow via the same ledger API. Different entity labels, same mechanics.

### `<sla-breach-policy-indicator>`

Visualisation of breach policy tiers and escalation consequences.

**Properties:**
- `tiers: TierDefinition[]` — array of `{ threshold, label, consequence, regulation? }`
- Or `endpoint: string` + `policyId: string`

**Renders:** Vertical step list. Each tier shows threshold duration, breach consequence (notification, escalation, regulatory filing), and driving regulation. Active tier highlighted based on time remaining. Uses `<sla-indicator>` internally.

**Generalises because:** Clinical has two-tier IND breach. AML has FinCEN deadlines. Any harness with `SlaBreachPolicy` needs this.

---

## Data Layer

### Dual-mode datasets

Every dataset supports two modes, switchable via build-time flag:

```typescript
const DEMO_MODE = import.meta.env.VITE_DEMO_MODE === "true";

export function dualDataset(
  id: string,
  endpoint: string,
  mockCsv: string,
  options?: DatasetOptions
) {
  return DEMO_MODE
    ? inlineDataset(id, mockCsv, options)
    : dataset(id, endpoint, options);
}
```

**Field name alignment:** Mock CSV column headers MUST match the live endpoint JSON field names. Both modes feed the same column definitions and expressions. Where the frontend needs a computed or machine-readable field (e.g., `slaTimeRemainingHours` as a numeric for colour-coded expressions), the backend must return that field alongside any human-readable variants. Currently, the mock CSV uses `slaTimeRemainingHours` (numeric) and `indStatus` while the backend `AdverseEventRow` returns `slaTimeRemaining` (formatted string) and `regulatorySubmissionStatus` — this must be aligned by adding `slaTimeRemainingHours: double` to the backend response and renaming `indStatus` to `regulatorySubmissionStatus` in the mock CSV.

This is the minimum viable step from the cross-repo dual-mode convention (aml#101). aml#101 proposes a richer model with temporal simulation — evolving data over time (SLA countdowns ticking, cases progressing, trust scores updating). Clinical adopts the simple toggle first; temporal simulation can be layered onto `dualDataset()` later without changing the wiring. The `dualDataset()` function signature is forward-compatible — a future `temporalDataset()` variant adds mutation schedules without touching existing call sites.

### Trial resolution

Production deployment is single-trial — one clinical instance per trial. The trial ID is configured via environment variable:

```typescript
const TRIAL_ID = DEMO_MODE
  ? "316e3846-4ea7-3b18-a6f7-e01ce6582a69"  // demo UUID (seeder-consistent)
  : import.meta.env.VITE_TRIAL_ID;
```

All dataset endpoints are parameterised by this resolved trial ID. Multi-trial support (trial picker, tenant routing) is out of scope — it would require a fundamentally different data architecture.

### Dataset inventory

| Dataset ID | Live endpoint | Used by |
|---|---|---|
| `work-items` | `/api/workitems?candidateGroups=clinical` | Operations SLA Health tab (aggregate reporting). Note: the Work Queue uses `<work-item-inbox>` which fetches via its own `endpoint` property, not this dataset. The `candidateGroups=clinical` query parameter filters for SLA dashboard aggregate stats, distinct from the inbox's identity-based per-user filtering. |
| `adverse-events` | `/api/trials/{TRIAL_ID}/adverse-events` | Safety Workbench list |
| `deviations` | `/api/trials/{TRIAL_ID}/deviations` | Protocol Workbench list |
| `trial-summary` | `/api/trials/{TRIAL_ID}/summary` | Operations Trial Dashboard |
| `sites` | `/api/trials/{TRIAL_ID}/sites` | Operations Trial Dashboard |
| `agents` | `/api/trials/{TRIAL_ID}/agents` | Operations Trust & Governance |
| `ledger-entries` | `/api/trials/{TRIAL_ID}/ledger-entries` | Audit Trail tabs |
| `patients` | `/api/trials/{TRIAL_ID}/patients` | Operations Trial Dashboard |
| `ae-precedents` | `/api/trials/{TRIAL_ID}/adverse-events/{aeId}/precedents` | Safety Precedents tab |
| `deviation-precedents` | `/api/trials/{TRIAL_ID}/deviations/{devId}/precedents` | Protocol Precedents tab |
| `ae-governance` | `/api/trials/{TRIAL_ID}/adverse-events/{aeId}/governance` | SUSAR Evaluation tab |
| `commitment` | `/api/trials/{TRIAL_ID}/deviations/{devId}/commitment` | PI Commitment tab |

### Detail-level datasets

Workbench detail tabs fetch data scoped to the selected entity:

- Precedents: parameterised by selected entity ID
- Audit trail: filtered by subject ID
- Governance (SUSAR): parameterised by AE ID
- Commitment lifecycle: parameterised by deviation ID

For live mode, these use parameterised `dataset()` calls — pages-ui does not currently support URL template variables (casehub-pages#49 is open), so detail datasets are created dynamically when a row is selected (new `dataset()` call with the entity ID interpolated into the URL string). For mock mode, detail data is defined inline in code (TypeScript objects), not as CSV files — keyed to the demo entity IDs (same deterministic UUIDs as the seeder). This is why the `mock/` directory has no CSV files for governance, commitment, or per-entity precedents: detail-level mock data lives in `datasets.ts` as inline constants.

**Lifecycle:** Replace-on-selection. When a new entity is selected, the previous detail datasets for that workbench are disposed (removed from the dataset manager, stopping any polling or active connections). New detail datasets are created with the selected entity's ID. At most one set of detail datasets is active per workbench at any time — selecting AE #5 replaces AE #3's datasets, not accumulates alongside them. This is implemented via a `replaceDetailDatasets(entityId)` helper that tears down the previous set before creating the new one.

**Current state:** The scaffolding pass creates `aePrecedentsDs` and `deviationPrecedentsDs` as static module-level exports with hardcoded demo entity IDs (`ae-demo-001`, `dev-demo-001`). In demo mode this is acceptable (seeder-consistent UUIDs). In live mode, these must be replaced with dynamic dataset creation driven by row selection. The `ae-governance` and `commitment` detail datasets are not yet created — they are detail-level datasets that require the dynamic creation mechanism. This is tracked as part of the row selection implementation.

### Existing REST endpoints

`TrialDashboardResource` has 10 endpoints: `summary`, `patients`, `adverseEvents`, `deviations`, `agents`, `governance`, `ledgerEntries`, `sites`, `aePrecedents`, `deviationPrecedents`. All stay unchanged. Work-items endpoint comes from casehub-work's existing REST API.

**New endpoint needed:** A commitment lifecycle endpoint (`/api/trials/{trialId}/deviations/{devId}/commitment`) must be added to `TrialDashboardResource` to serve the PI Commitment tab. This endpoint queries qhorus's `CommitmentStore` for the deviation's commitment and channel messages. No qhorus REST endpoint exists for commitments — clinical builds its own view-specific endpoint.

**Current state:** The endpoint exists but returns a flat `CommitmentLifecycleResponse` with deviation-level metadata only (deviationType, severity, piApprovalStatus, channelName, commandedAt, resolvedAt). The `<commitment-lifecycle>` component expects `CommitmentState` with nested `stages[]` (key, actor, timestamp, status) and `messages[]` (sender, content, timestamp). The endpoint must be updated to query `CommitmentStore` for the full stage/message data and return the `CommitmentState` shape. Until qhorus integration is complete, the PI Commitment tab shows "Commitment data unavailable."

---

## File Structure

```
runtime/src/main/webui/src/
├── index.ts                          # Entry: register components, loadSite()
├── app.ts                            # Root page: sidebar with 4 views
├── datasets.ts                       # dualDataset() helper + all dataset defs
├── mock/
│   ├── work-items.csv
│   ├── adverse-events.csv
│   ├── deviations.csv
│   ├── trial-summary.csv
│   ├── sites.csv
│   ├── agents.csv
│   ├── ledger-entries.csv
│   ├── patients.csv
│   ├── ae-precedents.csv
│   └── deviation-precedents.csv
├── stubs/
│   └── audit-trail-viewer.ts        # Clinical-local stub until blocks-ui#9
├── views/
│   ├── work-queue.ts                 # <work-item-inbox> with clinical groups
│   ├── safety-workbench.ts           # Split-pane AE list + tabbed detail
│   ├── protocol-workbench.ts         # Split-pane deviation list + tabbed detail
│   └── operations.ts                 # Tabbed: dashboard, trust, SLA, compliance, GDPR
├── components/                       # Promotion candidates (Lit web components)
│   ├── commitment-lifecycle.ts
│   ├── cbr-precedents-panel.ts
│   ├── trust-feedback-display.ts
│   ├── regulatory-compliance-summary.ts
│   ├── gdpr-erasure-action.ts
│   └── sla-breach-policy-indicator.ts
```

### Deletions from current UI

| File | Reason |
|------|--------|
| `theme.ts` | Replaced by `pages-ui-tokens` |
| `helpers.ts` | `actionButton()` / `alert()` replaced by blocks-ui components |
| `components/clinical-pi-approval.ts` | Replaced by `<approval-gate>` |
| `components/clinical-susar-gate.ts` | Replaced by `<approval-gate>` |
| `components/clinical-merkle-verify.ts` | Replaced by `<audit-trail-viewer>` (stub until #9) |
| `dashboard.ts` | Replaced by `app.ts` with sidebar |

### Retained for guided mode (separate concern)

| File | Disposition |
|------|------------|
| `narrative.ts` | Move to `guided/` directory |
| `guided/step1-overview.ts` through `step8-proof.ts` | Move to `guided/` directory |
| `explore/*.ts` | Content absorbed into workbench + operations views; originals deleted. Git history preserves them. Do not archive as working code — relative imports (`../datasets`) would break in a moved location, producing dead code with compile errors. |

---

## Error Strategy

blocks-ui components and pages-ui infrastructure handle most error states internally:

| Concern | Handled by |
|---------|-----------|
| Dataset fetch failure (HTTP 500, timeout) | `pages-data-table` displays inline error state; dataset manager retries per configured policy |
| Empty dataset (no results) | `pages-data-table` displays configurable `emptyMessage`. Clinical sets per-table messages: "No adverse events reported", "No deviations recorded", etc. |
| `<approval-gate>` POST failure | Component displays `_error` state inline with failure message |
| `<sla-indicator>` invalid deadline | Component handles gracefully (shows "—") |

**Clinical-specific error handling:**

- **Detail dataset fetch failure:** If a governance or precedent endpoint returns an error for the selected entity, the detail tab shows "Unable to load data — select another record or retry." The error does not propagate to the list pane.
- **Work Queue empty state:** "No pending tasks. All items are up to date."
- **Session expiry:** Handled at the platform level (pages-runtime auth integration). Not a clinical concern.
- **Commitment endpoint unavailable:** PI Commitment tab shows "Commitment data unavailable" with the deviation status from the list data as fallback context.

---

## Requirements on blocks-ui Components

For clinical to consume blocks-ui components, these API properties must hold:

1. **`<approval-gate>` configurable endpoint** — clinical has two distinct approval flows (PI auth, SUSAR gate) hitting different REST endpoints. Accepts endpoint URL and request body as properties. ✅ Already supported — `endpoint` property exists.
2. **`<approval-gate>` post-action pattern** — the `gate.decided` event carries `{gateId, outcome, resolution}` but does NOT forward the HTTP response payload. Clinical handles this by listening for `gate.decided`, then separately fetching the governance endpoint (`/trials/{trialId}/adverse-events/{aeId}/governance`) to get the updated trust score delta. The `<trust-feedback-display>` component renders the result. This decouples the gate from the response shape — the gate decides, clinical fetches context. Enhancement filed: blocks-ui#40 (response payload in event — nice-to-have, not blocking).
3. **`<audit-trail-viewer>` scoped verification** — clinical verifies Merkle chains at trial level and patient level (different verification URLs). Accepts verification endpoint URL.
4. **`<trust-score-panel>` custom dimensions** — clinical uses domain-specific trust dimensions (`safety-accuracy`, `eligibility-precision`, `protocol-adherence`). Accepts dimension labels and thresholds as properties.
5. **`<sla-indicator>` regulatory context** — optional property to display which regulation drives the SLA deadline.
6. **`<kpi-metric-row>` static data mode** — works with inline data from dataset lookup, not only from a dedicated metrics endpoint.

---

## Dependency & Sequencing

### Ready now (no blockers)

- Sidebar navigation structure
- Work Queue with `<work-item-inbox>`
- Safety Workbench list pane with `<pages-data-table>`
- Protocol Workbench list pane with `<pages-data-table>`
- Operations Trial Dashboard with `<kpi-metric-row>` + charts
- All 6 promotion candidate components (local Lit builds)
- `<approval-gate>` for SUSAR and IRB tabs
- `<sla-indicator>` for AE deadlines
- Dual-mode datasets
- Theme migration to `pages-ui-tokens`

### Blocked on blocks-ui

| Component | blocks-ui issue | Stub |
|---|---|---|
| `<audit-trail-viewer>` | #9 | DSL `table()` with ledger columns |
| `<trust-score-panel>` | #11 | DSL `table()` with trust columns |
| `<case-timeline>` | #10 | Omit initially |

### Blocked on AML promotion

| Component | Impact |
|---|---|
| `<case-workbench>` (aml#91) | Replace manual split-pane in both workbenches. View structure unchanged. |

### Build order

Each step below has two sub-phases: **layout** (structure, components, tabs, column definitions) and **wiring** (row selection, data binding, event handlers, property setting). Layout is scaffolded first across all views to validate the visual structure; wiring follows per-view.

1. **Scaffold** — sidebar, routing (`tree()`), datasets, theme, trial resolution, file structure ✅
2. **Promotion components** — build the 6 Lit components (`<commitment-lifecycle>`, `<cbr-precedents-panel>`, `<trust-feedback-display>`, `<regulatory-compliance-summary>`, `<gdpr-erasure-action>`, `<sla-breach-policy-indicator>`). Workbench tabs depend on these — they must exist before steps 3–5. ✅
3. **Work Queue** — `<work-item-inbox>` wired to clinical groups + navigation handler. Layout ✅. Wiring (identity property, navigation handler) blocked on blocks-ui#42 (`WorkItemResponse` missing `types` field).
4. **Safety Workbench** — AE list + detail tabs (overview, SUSAR, trust, regulatory, precedents, audit). Layout ✅. Wiring: row selection → detail dataset creation → tab data binding → component property setting.
5. **Protocol Workbench** — deviation list + detail tabs (overview, commitment, IRB, precedents, audit). Layout ✅. Wiring: row selection → detail dataset creation → tab data binding → component property setting.
6. **Operations** — trial dashboard, trust, SLA, compliance, GDPR tabs. Layout ✅. Wiring: mostly complete (metrics, charts, tables bound to datasets); GDPR erasure action needs confirmation dialog.
7. **Commitment endpoint** — new `TrialDashboardResource` method for PI Commitment tab data. Endpoint exists ✅. qhorus `CommitmentStore` integration pending (returns flat deviation metadata, needs stage/message data).
8. **Swap-ins** — replace stubs when blocks-ui #9, #10, #11 and case-workbench land

---

## Cross-Repo Coordination

### blocks-ui#35 — parent epic

Clinical's child epic is blocks-ui#38. Updated parent epic includes clinical's promotion candidates and cross-repo overlap watch.

### Overlap with AML

| Pattern | Resolution |
|---------|------------|
| `<approval-gate>` post-action display | Both clinical (trust score delta) and OpenClaw (agent output) need this — coordinate API surface |
| Regulatory compliance grid | Clinical builds first (FDA/GCP/GDPR), AML validates shape (FinCEN/FATF/GDPR) before promotion |
| GDPR erasure action | Same ledger API, different entity labels — configurable `subjectLabel` property |
| Dual-mode datasets | Convention proposed on blocks-ui#35; AML issue casehubio/aml#101 |

### Overlap with OpenClaw

| Pattern | Resolution |
|---------|------------|
| `<approval-gate>` extensibility | OpenClaw merges gate-approval-modal into approval-gate; clinical consumes the result |
| Chronological event feed | OpenClaw promotes `<channel-feed>`; clinical consumes `<audit-trail-viewer>` for ledger entries |

---

## Related Issues

- casehubio/blocks-ui#35 — parent cross-repo migration tracking epic
- casehubio/blocks-ui#38 — clinical child epic
- casehubio/blocks-ui#9 — Audit Trail Viewer (blocks clinical audit tab)
- casehubio/blocks-ui#10 — Case Timeline
- casehubio/blocks-ui#11 — Trust Score Panel (blocks clinical trust tab)
- casehubio/aml#91 — AML workbench UI port (case-workbench promotion source)
- casehubio/aml#101 — dual-mode dataset proposal
- casehubio/blocks-ui#36 — OpenClaw migration plan
- casehubio/blocks-ui#37 — AML migration plan
- casehubio/blocks-ui#40 — approval-gate: response payload in gate.decided event (nice-to-have)
- casehubio/blocks-ui#42 — WorkItemResponse TypeScript type missing `types` field (blocks Work Queue navigation)
