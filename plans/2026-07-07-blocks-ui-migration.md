# Clinical Product UI — blocks-ui Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #121 — feat: product UI — blocks-ui migration
**Issue group:** #121, blocks-ui#38

**Goal:** Rebuild clinical's UI as a functioning product with 4 operational views (Work Queue, Safety Workbench, Protocol Workbench, Operations) using blocks-ui shared components and 6 clinical-built promotion candidates.

**Architecture:** Sidebar navigation via pages-runtime `tree()`. Two split-pane workbenches (Safety, Protocol) for distinct AE and deviation workflows. Dual-mode datasets (inline mock CSV + live REST endpoints) switchable via `VITE_DEMO_MODE` build flag. Six Lit web components built locally for eventual blocks-ui promotion.

**Tech Stack:** TypeScript 5.6+, Lit 3, esbuild, @casehubio/pages-ui + pages-runtime, @casehubio/blocks-ui-* components, vitest + happy-dom for testing. Java 21 / Quarkus for the commitment endpoint.

## Global Constraints

- All pages-ui DSL imports from `@casehubio/pages-ui`
- All blocks-ui component imports from `@casehubio/blocks-ui-*` packages
- Column IDs cast as `never` (pages-ui convention — see AML reference)
- Lit components use `@casehubio/pages-ui-tokens` CSS custom properties (`--pages-*`)
- Lit components emit events via `emitPagesEvent()` from `@casehubio/pages-component`
- Dataset references use `.uuid` property in `lookup()` calls
- Mock CSV uses header row + newline-separated records format
- TRIAL_ID: `316e3846-4ea7-3b18-a6f7-e01ce6582a69` (demo mode); `import.meta.env.VITE_TRIAL_ID` (production)
- All commits reference #121: `Refs #121` or `Closes #121`
- TDD: write failing test before implementation
- IntelliJ MCP for all code navigation and refactoring

---

### Task 1: Scaffold — Package, Build, App Shell, Datasets

**Files:**
- Modify: `runtime/src/main/resources/webui/package.json`
- Modify: `runtime/src/main/resources/webui/tsconfig.json`
- Create: `runtime/src/main/resources/webui/src/app.ts`
- Create: `runtime/src/main/resources/webui/src/datasets.ts` (rewrite)
- Create: `runtime/src/main/resources/webui/src/mock/adverse-events.csv`
- Create: `runtime/src/main/resources/webui/src/mock/deviations.csv`
- Create: `runtime/src/main/resources/webui/src/mock/trial-summary.csv`
- Create: `runtime/src/main/resources/webui/src/mock/sites.csv`
- Create: `runtime/src/main/resources/webui/src/mock/agents.csv`
- Create: `runtime/src/main/resources/webui/src/mock/ledger-entries.csv`
- Create: `runtime/src/main/resources/webui/src/mock/patients.csv`
- Create: `runtime/src/main/resources/webui/src/mock/ae-precedents.csv`
- Create: `runtime/src/main/resources/webui/src/mock/deviation-precedents.csv`
- Create: `runtime/src/main/resources/webui/src/mock/work-items.csv`
- Modify: `runtime/src/main/resources/webui/src/index.ts` (rewrite)
- Create: `runtime/src/main/resources/webui/src/views/work-queue.ts` (placeholder)
- Create: `runtime/src/main/resources/webui/src/views/safety-workbench.ts` (placeholder)
- Create: `runtime/src/main/resources/webui/src/views/protocol-workbench.ts` (placeholder)
- Create: `runtime/src/main/resources/webui/src/views/operations.ts` (placeholder)
- Create: `runtime/src/main/resources/webui/vitest.config.ts`
- Test: `runtime/src/main/resources/webui/src/__tests__/datasets.test.ts`
- Test: `runtime/src/main/resources/webui/src/__tests__/app.test.ts`

**Interfaces:**
- Consumes: nothing (foundational task)
- Produces:
  - `dualDataset(id, endpoint, mockCsv, options?)` — dataset factory
  - `TRIAL_ID: string` — resolved trial UUID
  - `DEMO_MODE: boolean` — build-time flag
  - All dataset exports: `adverseEventsDs`, `deviationsDs`, `trialSummaryDs`, `sitesDs`, `agentsDs`, `ledgerEntriesDs`, `patientsDs`, `workItemsDs`, `aePrecedentsDs`, `deviationPrecedentsDs`
  - `app` — root page Component with sidebar
  - `workQueue()`, `safetyWorkbench()`, `protocolWorkbench()`, `operations()` — view functions (placeholders returning `markdown("Coming soon")`)

- [ ] **Step 1: Update package.json with new dependencies**

Add blocks-ui components, Lit, pages-ui-tokens, vitest, happy-dom:

```json
{
  "name": "casehub-clinical-webui",
  "private": true,
  "scripts": {
    "build": "node esbuild.config.mjs",
    "dev": "node esbuild.config.mjs --watch",
    "typecheck": "tsc --noEmit",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "lit": "^3.0.0",
    "@casehubio/pages-runtime": "file:../../../../../pages/packages/pages-runtime",
    "@casehubio/pages-ui": "file:../../../../../pages/packages/pages-ui",
    "@casehubio/pages-component": "file:../../../../../pages/packages/pages-component",
    "@casehubio/pages-data": "file:../../../../../pages/packages/pages-data",
    "@casehubio/pages-ui-tokens": "file:../../../../../pages/packages/pages-ui-tokens",
    "@casehubio/blocks-ui-core": "file:../../../../../blocks-ui/packages/blocks-ui-core",
    "@casehubio/blocks-ui-approval-gate": "file:../../../../../blocks-ui/components/approval-gate",
    "@casehubio/blocks-ui-sla-indicator": "file:../../../../../blocks-ui/components/sla-indicator",
    "@casehubio/blocks-ui-kpi-metric-row": "file:../../../../../blocks-ui/components/kpi-metric-row",
    "@casehubio/blocks-ui-data-table": "file:../../../../../blocks-ui/components/data-table",
    "@casehubio/blocks-ui-work-item-inbox": "file:../../../../../blocks-ui/components/work-item-inbox"
  },
  "devDependencies": {
    "esbuild": "^0.25.0",
    "typescript": "^5.6.0",
    "vitest": "^3.0.0",
    "happy-dom": "^17.0.0"
  }
}
```

- [ ] **Step 2: Update tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "outDir": "dist",
    "declaration": false,
    "noEmit": true,
    "experimentalDecorators": true,
    "useDefineForClassFields": false
  },
  "include": ["src", "src/components"]
}
```

Key additions: `experimentalDecorators` and `useDefineForClassFields: false` for Lit decorators.

- [ ] **Step 3: Create vitest.config.ts**

```typescript
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "happy-dom",
    include: ["src/**/*.test.ts"],
  },
});
```

- [ ] **Step 4: Write failing test for dualDataset**

```typescript
// src/__tests__/datasets.test.ts
import { describe, it, expect, vi } from "vitest";

describe("dualDataset", () => {
  it("returns inlineDataset in demo mode", async () => {
    vi.stubEnv("VITE_DEMO_MODE", "true");
    const { dualDataset } = await import("../datasets.js");
    const ds = dualDataset("test", "/api/test", "id,name\n1,Alpha\n2,Beta");
    expect(ds).toBeDefined();
    expect(ds.uuid).toBeDefined();
    vi.unstubAllEnvs();
  });

  it("returns dataset in production mode", async () => {
    vi.stubEnv("VITE_DEMO_MODE", "false");
    vi.stubEnv("VITE_TRIAL_ID", "test-trial-uuid");
    const { dualDataset } = await import("../datasets.js");
    const ds = dualDataset("test-prod", "/api/test", "id,name\n1,Alpha");
    expect(ds).toBeDefined();
    expect(ds.uuid).toBeDefined();
    vi.unstubAllEnvs();
  });

  it("resolves TRIAL_ID from env in production mode", async () => {
    vi.stubEnv("VITE_DEMO_MODE", "false");
    vi.stubEnv("VITE_TRIAL_ID", "custom-trial-uuid");
    const { TRIAL_ID } = await import("../datasets.js");
    expect(TRIAL_ID).toBe("custom-trial-uuid");
    vi.unstubAllEnvs();
  });

  it("uses hardcoded demo UUID in demo mode", async () => {
    vi.stubEnv("VITE_DEMO_MODE", "true");
    const { TRIAL_ID } = await import("../datasets.js");
    expect(TRIAL_ID).toBe("316e3846-4ea7-3b18-a6f7-e01ce6582a69");
    vi.unstubAllEnvs();
  });
});
```

- [ ] **Step 5: Run test to verify it fails**

Run: `cd runtime/src/main/resources/webui && npx vitest run src/__tests__/datasets.test.ts`
Expected: FAIL — module not found

- [ ] **Step 6: Implement datasets.ts**

```typescript
// src/datasets.ts
import { dataset, inlineDataset } from "@casehubio/pages-ui";
import type { DatasetOptions } from "@casehubio/pages-ui";

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
  : import.meta.env.VITE_TRIAL_ID;

export function dualDataset(
  id: string,
  endpoint: string,
  mockCsv: string,
  options?: DatasetOptions,
) {
  return DEMO_MODE
    ? inlineDataset(id, mockCsv, options)
    : dataset(id, endpoint, options);
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
  { expression: "[$]" },
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
```

- [ ] **Step 7: Create mock CSV files**

Create all 10 CSV files in `src/mock/`. Each matches the response shape of its live endpoint. Example for `adverse-events.csv`:

```csv
id,grade,eventType,patientId,siteId,siteName,reportedAt,slaDeadline,slaTimeRemainingHours,escalationStatus,indStatus,unexpected,suspected
ae-001,GRADE_4,HEPATOTOXICITY,pat-001,site-a,Memorial Cancer Center,2026-07-01T10:00:00Z,2026-07-02T10:00:00Z,18.5,COMPLETED,FILED,true,true
ae-002,GRADE_2,NAUSEA,pat-002,site-a,Memorial Cancer Center,2026-07-02T14:00:00Z,2026-07-09T14:00:00Z,150.0,REPORTED,,false,false
ae-003,GRADE_4,NEUTROPENIA,pat-003,site-b,Johns Hopkins Oncology,2026-07-03T08:00:00Z,2026-07-04T08:00:00Z,-2.5,ESCALATED,PENDING,true,true
ae-004,GRADE_3,THROMBOCYTOPENIA,pat-004,site-a,Memorial Cancer Center,2026-07-04T11:00:00Z,2026-07-05T11:00:00Z,6.0,REQUESTED,,true,false
ae-005,GRADE_1,FATIGUE,pat-005,site-c,Mayo Clinic Research,2026-07-05T09:00:00Z,2026-07-12T09:00:00Z,168.0,REPORTED,,false,false
```

Create similar CSV files for each dataset. Each file must have realistic clinical data with correct field names matching `TrialDashboardResource` response DTOs.

- [ ] **Step 8: Write failing test for app shell**

```typescript
// src/__tests__/app.test.ts
import { describe, it, expect } from "vitest";

describe("app shell", () => {
  it("exports an app component", async () => {
    const { app } = await import("../app.js");
    expect(app).toBeDefined();
  });

  it("exports four view functions", async () => {
    const { workQueue, safetyWorkbench, protocolWorkbench, operations } =
      await import("../views/work-queue.js")
        .then(() => import("../views/safety-workbench.js"))
        .then(() => import("../views/protocol-workbench.js"))
        .then(() => import("../views/operations.js"));

    expect(workQueue).toBeDefined();
    expect(safetyWorkbench).toBeDefined();
    expect(protocolWorkbench).toBeDefined();
    expect(operations).toBeDefined();
  });
});
```

- [ ] **Step 9: Implement app.ts and view placeholders**

```typescript
// src/app.ts
import { page, tree } from "@casehubio/pages-ui";
import { workQueue, workQueueDatasets } from "./views/work-queue.js";
import { safetyWorkbench, safetyWorkbenchDatasets } from "./views/safety-workbench.js";
import { protocolWorkbench, protocolWorkbenchDatasets } from "./views/protocol-workbench.js";
import { operations, operationsDatasets } from "./views/operations.js";

export const app = page("CaseHub Clinical",
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
```

```typescript
// src/views/work-queue.ts
import { markdown } from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";

export function workQueue(): Component {
  return markdown("## Work Queue\n\nComing soon.");
}

export const workQueueDatasets: unknown[] = [];
```

Create identical placeholder files for `safety-workbench.ts`, `protocol-workbench.ts`, `operations.ts` — each exporting a view function and a datasets array.

```typescript
// src/index.ts
import { loadSite } from "@casehubio/pages-runtime";
import { app } from "./app.js";

const container = document.getElementById("app");
if (container) {
  loadSite(container, app).catch(console.error);
}
```

- [ ] **Step 10: Run tests and verify they pass**

Run: `cd runtime/src/main/resources/webui && npm install && npx vitest run`
Expected: All tests PASS

- [ ] **Step 11: Verify build succeeds**

Run: `cd runtime/src/main/resources/webui && npm run build`
Expected: `dist/app.js` generated without errors

- [ ] **Step 12: Commit**

```
feat(#121): scaffold — sidebar, dual-mode datasets, app shell

Refs #121
```

---

### Task 2: Promotion Component — `<commitment-lifecycle>`

**Files:**
- Create: `runtime/src/main/resources/webui/src/components/commitment-lifecycle.ts`
- Test: `runtime/src/main/resources/webui/src/__tests__/commitment-lifecycle.test.ts`

**Interfaces:**
- Consumes: `@casehubio/pages-ui-tokens` (CSS tokens), `emitPagesEvent` from `@casehubio/pages-component`
- Produces: `ClinicalCommitmentLifecycle` class registered as `<commitment-lifecycle>`. Properties: `commitmentId: string`, `endpoint: string`, `stages: StageDefinition[]`. Events: `commitment.stage-changed`.

- [ ] **Step 1: Write failing test**

```typescript
// src/__tests__/commitment-lifecycle.test.ts
import { describe, it, expect, beforeAll } from "vitest";
import { ClinicalCommitmentLifecycle } from "../components/commitment-lifecycle.js";

describe("ClinicalCommitmentLifecycle", () => {
  beforeAll(() => {
    if (!customElements.get("commitment-lifecycle")) {
      customElements.define("commitment-lifecycle", ClinicalCommitmentLifecycle);
    }
  });

  it("is a valid custom element", () => {
    const el = document.createElement("commitment-lifecycle") as ClinicalCommitmentLifecycle;
    expect(el).toBeInstanceOf(HTMLElement);
  });

  it("has default stages matching qhorus lifecycle", () => {
    const el = document.createElement("commitment-lifecycle") as ClinicalCommitmentLifecycle;
    expect(el.stages).toHaveLength(4);
    expect(el.stages.map(s => s.key)).toEqual(["COMMANDED", "ACKNOWLEDGED", "DONE", "DECLINED"]);
  });

  it("renders empty state when no commitmentId", async () => {
    const el = document.createElement("commitment-lifecycle") as ClinicalCommitmentLifecycle;
    document.body.appendChild(el);
    await el.updateComplete;
    expect(el.shadowRoot?.textContent).toContain("No commitment selected");
    el.remove();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd runtime/src/main/resources/webui && npx vitest run src/__tests__/commitment-lifecycle.test.ts`
Expected: FAIL — module not found

- [ ] **Step 3: Implement commitment-lifecycle.ts**

```typescript
// src/components/commitment-lifecycle.ts
import { LitElement, html, css } from "lit";
import { property, state } from "lit/decorators.js";
import { emitPagesEvent } from "@casehubio/pages-component";

export interface StageDefinition {
  readonly key: string;
  readonly label: string;
  readonly icon?: string;
}

interface CommitmentState {
  readonly id: string;
  readonly currentStage: string;
  readonly stages: ReadonlyArray<{
    readonly key: string;
    readonly actor?: string;
    readonly timestamp?: string;
    readonly status: "completed" | "active" | "pending" | "failed";
  }>;
  readonly messages?: ReadonlyArray<{
    readonly sender: string;
    readonly content: string;
    readonly timestamp: string;
  }>;
}

const DEFAULT_STAGES: StageDefinition[] = [
  { key: "COMMANDED", label: "Commanded" },
  { key: "ACKNOWLEDGED", label: "Acknowledged" },
  { key: "DONE", label: "Done" },
  { key: "DECLINED", label: "Declined" },
];

export class ClinicalCommitmentLifecycle extends LitElement {
  static styles = css`
    :host { display: block; font-family: var(--pages-font-family, sans-serif); }
    .timeline { display: flex; align-items: center; gap: var(--pages-space-4, 1rem); padding: var(--pages-space-4, 1rem) 0; }
    .stage { display: flex; flex-direction: column; align-items: center; gap: var(--pages-space-2, 0.5rem); min-width: 100px; }
    .stage-node { width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: 600; font-size: 14px; }
    .stage-node--completed { background: var(--pages-accent-9, #27ae60); color: white; }
    .stage-node--active { background: var(--pages-accent-9, #3498db); color: white; animation: pulse 2s infinite; }
    .stage-node--pending { background: var(--pages-neutral-6, #bdc3c7); color: white; }
    .stage-node--failed { background: var(--pages-red-9, #e74c3c); color: white; }
    .stage-label { font-size: 12px; color: var(--pages-neutral-11, #7f8c8d); text-align: center; }
    .stage-actor { font-size: 11px; color: var(--pages-neutral-9, #95a5a6); }
    .stage-time { font-size: 10px; color: var(--pages-neutral-8, #bdc3c7); }
    .connector { flex: 1; height: 2px; background: var(--pages-neutral-6, #bdc3c7); min-width: 20px; }
    .connector--completed { background: var(--pages-accent-9, #27ae60); }
    .messages { margin-top: var(--pages-space-4, 1rem); border-top: 1px solid var(--pages-neutral-4, #eee); padding-top: var(--pages-space-4, 1rem); }
    .message { padding: var(--pages-space-2, 0.5rem); margin-bottom: var(--pages-space-2, 0.5rem); background: var(--pages-neutral-2, #f8f9fa); border-radius: var(--pages-radius-2, 4px); }
    .message-sender { font-weight: 600; font-size: 12px; }
    .message-content { font-size: 13px; margin-top: 4px; }
    .empty { color: var(--pages-neutral-9, #95a5a6); font-style: italic; padding: var(--pages-space-4, 1rem); }
    @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.7; } }
  `;

  @property() commitmentId = "";
  @property() endpoint = "/api/commitments/{id}";
  @property({ attribute: false }) stages: StageDefinition[] = DEFAULT_STAGES;

  @state() private _commitment: CommitmentState | null = null;
  @state() private _loading = false;
  @state() private _error = "";

  connectedCallback() {
    super.connectedCallback();
    if (this.commitmentId) this._fetchCommitment();
  }

  updated(changed: Map<string, unknown>) {
    if (changed.has("commitmentId") && this.commitmentId) {
      this._fetchCommitment();
    }
  }

  private async _fetchCommitment() {
    this._loading = true;
    this._error = "";
    try {
      const url = this.endpoint.replace("{id}", this.commitmentId);
      const res = await fetch(url);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      this._commitment = await res.json();
    } catch (e) {
      this._error = e instanceof Error ? e.message : "Failed to load";
    } finally {
      this._loading = false;
    }
  }

  render() {
    if (!this.commitmentId) return html`<div class="empty">No commitment selected</div>`;
    if (this._loading) return html`<div class="empty">Loading commitment...</div>`;
    if (this._error) return html`<div class="empty">Commitment data unavailable</div>`;
    if (!this._commitment) return html`<div class="empty">No commitment data</div>`;

    const stageLabels = new Map(this.stages.map(s => [s.key, s.label]));
    const stageStates = this._commitment.stages;

    return html`
      <div class="timeline">
        ${stageStates.map((s, i) => html`
          ${i > 0 ? html`<div class="connector ${s.status === "completed" ? "connector--completed" : ""}"></div>` : ""}
          <div class="stage">
            <div class="stage-node stage-node--${s.status}">${i + 1}</div>
            <div class="stage-label">${stageLabels.get(s.key) ?? s.key}</div>
            ${s.actor ? html`<div class="stage-actor">${s.actor}</div>` : ""}
            ${s.timestamp ? html`<div class="stage-time">${new Date(s.timestamp).toLocaleString()}</div>` : ""}
          </div>
        `)}
      </div>
      ${this._commitment.messages?.length ? html`
        <div class="messages">
          <strong>Channel Messages</strong>
          ${this._commitment.messages.map(m => html`
            <div class="message">
              <div class="message-sender">${m.sender} &middot; ${new Date(m.timestamp).toLocaleString()}</div>
              <div class="message-content">${m.content}</div>
            </div>
          `)}
        </div>
      ` : ""}
    `;
  }
}
```

- [ ] **Step 4: Run tests and verify they pass**

Run: `cd runtime/src/main/resources/webui && npx vitest run src/__tests__/commitment-lifecycle.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```
feat(#121): add <commitment-lifecycle> promotion component

Refs #121
```

---

### Task 3: Promotion Components — CBR Precedents + Trust Feedback + Regulatory + GDPR + SLA Breach

**Files:**
- Create: `runtime/src/main/resources/webui/src/components/cbr-precedents-panel.ts`
- Create: `runtime/src/main/resources/webui/src/components/trust-feedback-display.ts`
- Create: `runtime/src/main/resources/webui/src/components/regulatory-compliance-summary.ts`
- Create: `runtime/src/main/resources/webui/src/components/gdpr-erasure-action.ts`
- Create: `runtime/src/main/resources/webui/src/components/sla-breach-policy-indicator.ts`
- Test: `runtime/src/main/resources/webui/src/__tests__/promotion-components.test.ts`

**Interfaces:**
- Consumes: `@casehubio/pages-ui-tokens`, `emitPagesEvent`
- Produces:
  - `ClinicalCbrPrecedentsPanel` — `endpoint: string`, `columns: ColumnDef[]`, `emptyMessage: string`
  - `ClinicalTrustFeedbackDisplay` — `gateDecision: object`, `compact: boolean`
  - `ClinicalRegulatoryComplianceSummary` — `requirements: RequirementDefinition[]`, `endpoint: string`
  - `ClinicalGdprErasureAction` — `endpoint: string`, `subjectLabel: string`, `reasonOptions: string[]`
  - `ClinicalSlaBreachPolicyIndicator` — `tiers: TierDefinition[]`, `timeRemaining: number`

Each component follows the same Lit pattern as Task 2. Implementation details for each:

**`<cbr-precedents-panel>`:** Fetches from `endpoint`, renders a table with similarity percentage bar, outcome badge, resolution time. Emits `precedent.selected` on row click.

**`<trust-feedback-display>`:** Renders a card with gate decision badge, approver, attestation verdict, trust score delta (before → after with coloured arrow), dimension. `compact` mode shows single line.

**`<regulatory-compliance-summary>`:** Renders a table of regulatory requirements. Each row: regulation name, requirement, mechanism, status badge (MET green, PARTIAL yellow, GAP orange, BREACHED red), evidence link.

**`<gdpr-erasure-action>`:** Subject ID input, reason dropdown, confirm dialog, receipt display. POST to `endpoint` on confirm. Shows ALREADY_WITHDRAWN if idempotent response.

**`<sla-breach-policy-indicator>`:** Vertical step list of `tiers`. Each shows threshold, consequence, regulation. Highlights active tier based on `timeRemaining`. Uses `<sla-indicator>` from blocks-ui internally for countdown.

- [ ] **Step 1: Write failing tests for all five components**

```typescript
// src/__tests__/promotion-components.test.ts
import { describe, it, expect, beforeAll } from "vitest";
import { ClinicalCbrPrecedentsPanel } from "../components/cbr-precedents-panel.js";
import { ClinicalTrustFeedbackDisplay } from "../components/trust-feedback-display.js";
import { ClinicalRegulatoryComplianceSummary } from "../components/regulatory-compliance-summary.js";
import { ClinicalGdprErasureAction } from "../components/gdpr-erasure-action.js";
import { ClinicalSlaBreachPolicyIndicator } from "../components/sla-breach-policy-indicator.js";

describe("promotion components", () => {
  beforeAll(() => {
    const defs: [string, CustomElementConstructor][] = [
      ["cbr-precedents-panel", ClinicalCbrPrecedentsPanel],
      ["trust-feedback-display", ClinicalTrustFeedbackDisplay],
      ["regulatory-compliance-summary", ClinicalRegulatoryComplianceSummary],
      ["gdpr-erasure-action", ClinicalGdprErasureAction],
      ["sla-breach-policy-indicator", ClinicalSlaBreachPolicyIndicator],
    ];
    for (const [name, ctor] of defs) {
      if (!customElements.get(name)) customElements.define(name, ctor);
    }
  });

  it("cbr-precedents-panel renders empty state", async () => {
    const el = document.createElement("cbr-precedents-panel") as ClinicalCbrPrecedentsPanel;
    document.body.appendChild(el);
    await el.updateComplete;
    expect(el.shadowRoot?.textContent).toContain("No similar cases found");
    el.remove();
  });

  it("trust-feedback-display renders decision card", async () => {
    const el = document.createElement("trust-feedback-display") as ClinicalTrustFeedbackDisplay;
    el.gateDecision = {
      decision: "APPROVED",
      investigator: "Dr. Smith",
      attestation: "ENDORSED",
      trustScoreBefore: 0.75,
      trustScoreAfter: 0.82,
      dimension: "safety-accuracy",
    };
    document.body.appendChild(el);
    await el.updateComplete;
    const text = el.shadowRoot?.textContent ?? "";
    expect(text).toContain("APPROVED");
    expect(text).toContain("Dr. Smith");
    expect(text).toContain("0.75");
    expect(text).toContain("0.82");
    el.remove();
  });

  it("trust-feedback-display compact mode renders single line", async () => {
    const el = document.createElement("trust-feedback-display") as ClinicalTrustFeedbackDisplay;
    el.compact = true;
    el.gateDecision = {
      decision: "APPROVED",
      investigator: "Dr. Smith",
      attestation: "ENDORSED",
      trustScoreBefore: 0.75,
      trustScoreAfter: 0.82,
      dimension: "safety-accuracy",
    };
    document.body.appendChild(el);
    await el.updateComplete;
    const children = el.shadowRoot?.querySelectorAll(".compact") ?? [];
    expect(children.length).toBeGreaterThan(0);
    el.remove();
  });

  it("regulatory-compliance-summary renders requirements", async () => {
    const el = document.createElement("regulatory-compliance-summary") as ClinicalRegulatoryComplianceSummary;
    el.requirements = [
      { regulation: "FDA 21 CFR 312.32", requirement: "Expedited safety reporting", mechanism: "SLA WorkItem", status: "MET" },
      { regulation: "GDPR Art.17", requirement: "Right to erasure", mechanism: "LedgerErasureService", status: "MET" },
    ];
    document.body.appendChild(el);
    await el.updateComplete;
    const text = el.shadowRoot?.textContent ?? "";
    expect(text).toContain("FDA 21 CFR 312.32");
    expect(text).toContain("GDPR Art.17");
    el.remove();
  });

  it("gdpr-erasure-action renders input form", async () => {
    const el = document.createElement("gdpr-erasure-action") as ClinicalGdprErasureAction;
    el.subjectLabel = "Patient";
    document.body.appendChild(el);
    await el.updateComplete;
    const text = el.shadowRoot?.textContent ?? "";
    expect(text).toContain("Patient");
    expect(el.shadowRoot?.querySelector("input")).toBeTruthy();
    el.remove();
  });

  it("sla-breach-policy-indicator renders tiers", async () => {
    const el = document.createElement("sla-breach-policy-indicator") as ClinicalSlaBreachPolicyIndicator;
    el.tiers = [
      { threshold: 0.75, label: "Warning", consequence: "Sponsor notified", regulation: "ICH E6(R3)" },
      { threshold: 1.0, label: "Breach", consequence: "Regulatory filing required", regulation: "21 CFR 312.32" },
    ];
    document.body.appendChild(el);
    await el.updateComplete;
    const text = el.shadowRoot?.textContent ?? "";
    expect(text).toContain("Warning");
    expect(text).toContain("Breach");
    expect(text).toContain("21 CFR 312.32");
    el.remove();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd runtime/src/main/resources/webui && npx vitest run src/__tests__/promotion-components.test.ts`
Expected: FAIL — modules not found

- [ ] **Step 3: Implement all five components**

Each follows the Lit pattern from Task 2. Use `LitElement` base class, `@property` decorators, shadow DOM CSS with `--pages-*` tokens, `emitPagesEvent()` for events. Complete source for each component — see spec §Promotion Candidates for property lists, render descriptions, and generalisation rationale.

Key implementation notes:
- `cbr-precedents-panel`: fetch from `endpoint` on `connectedCallback`, render table rows with `map()`, percentage bar uses CSS `width: ${similarity}%`
- `trust-feedback-display`: pure render from `gateDecision` property — no fetch. Arrow colour: green if after > before, red if lower
- `regulatory-compliance-summary`: static render from `requirements` array. Status badge colours: MET=#27ae60, PARTIAL=#f39c12, GAP=#e67e22, BREACHED=#e74c3c
- `gdpr-erasure-action`: form state managed with `@state()`. POST on confirm button click. Show receipt on success.
- `sla-breach-policy-indicator`: highlight active tier by comparing `timeRemaining` against `threshold * totalDuration`. Import `<sla-indicator>` from blocks-ui.

- [ ] **Step 4: Run tests and verify they pass**

Run: `cd runtime/src/main/resources/webui && npx vitest run src/__tests__/promotion-components.test.ts`
Expected: All PASS

- [ ] **Step 5: Commit**

```
feat(#121): add 5 promotion components — CBR, trust, regulatory, GDPR, SLA breach

Refs #121
```

---

### Task 4: Work Queue View

**Files:**
- Modify: `runtime/src/main/resources/webui/src/views/work-queue.ts`
- Modify: `runtime/src/main/resources/webui/src/index.ts` (register components)
- Test: `runtime/src/main/resources/webui/src/__tests__/work-queue.test.ts`

**Interfaces:**
- Consumes: `workItemsDs` from datasets, `<work-item-inbox>` from blocks-ui
- Produces: `workQueue(): Component`, `workQueueDatasets: DatasetDef[]`

- [ ] **Step 1: Write failing test**

```typescript
// src/__tests__/work-queue.test.ts
import { describe, it, expect } from "vitest";
import { workQueue, workQueueDatasets } from "../views/work-queue.js";

describe("work-queue view", () => {
  it("returns a defined component", () => {
    const component = workQueue();
    expect(component).toBeDefined();
  });

  it("exports datasets array", () => {
    expect(workQueueDatasets).toBeInstanceOf(Array);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement work-queue.ts**

```typescript
// src/views/work-queue.ts
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
```

Note: `<work-item-inbox>` needs `identity` set programmatically. Update `index.ts` to configure identity after `loadSite()`:

```typescript
// src/index.ts
import { loadSite } from "@casehubio/pages-runtime";
import { app } from "./app.js";
import { ClinicalCommitmentLifecycle } from "./components/commitment-lifecycle.js";
import { ClinicalCbrPrecedentsPanel } from "./components/cbr-precedents-panel.js";
import { ClinicalTrustFeedbackDisplay } from "./components/trust-feedback-display.js";
import { ClinicalRegulatoryComplianceSummary } from "./components/regulatory-compliance-summary.js";
import { ClinicalGdprErasureAction } from "./components/gdpr-erasure-action.js";
import { ClinicalSlaBreachPolicyIndicator } from "./components/sla-breach-policy-indicator.js";

const components: [string, CustomElementConstructor][] = [
  ["commitment-lifecycle", ClinicalCommitmentLifecycle],
  ["cbr-precedents-panel", ClinicalCbrPrecedentsPanel],
  ["trust-feedback-display", ClinicalTrustFeedbackDisplay],
  ["regulatory-compliance-summary", ClinicalRegulatoryComplianceSummary],
  ["gdpr-erasure-action", ClinicalGdprErasureAction],
  ["sla-breach-policy-indicator", ClinicalSlaBreachPolicyIndicator],
];

for (const [name, ctor] of components) {
  if (!customElements.get(name)) customElements.define(name, ctor);
}

const container = document.getElementById("app");
if (container) {
  loadSite(container, app).catch(console.error);
}
```

- [ ] **Step 4: Run tests and verify they pass**

- [ ] **Step 5: Commit**

```
feat(#121): work queue view with <work-item-inbox>

Refs #121
```

---

### Task 5: Safety Workbench View

**Files:**
- Modify: `runtime/src/main/resources/webui/src/views/safety-workbench.ts`
- Create: `runtime/src/main/resources/webui/src/stubs/audit-trail-viewer.ts`
- Test: `runtime/src/main/resources/webui/src/__tests__/safety-workbench.test.ts`

**Interfaces:**
- Consumes: `adverseEventsDs`, `aePrecedentsDs`, `ledgerEntriesDs`, `agentsDs` from datasets. `<approval-gate>`, `<sla-indicator>` from blocks-ui. `<trust-feedback-display>`, `<cbr-precedents-panel>`, `<sla-breach-policy-indicator>` from Task 2-3.
- Produces: `safetyWorkbench(): Component`, `safetyWorkbenchDatasets: DatasetDef[]`

- [ ] **Step 1: Write failing test**

```typescript
// src/__tests__/safety-workbench.test.ts
import { describe, it, expect } from "vitest";
import { safetyWorkbench, safetyWorkbenchDatasets } from "../views/safety-workbench.js";

describe("safety-workbench view", () => {
  it("returns a defined component", () => {
    const component = safetyWorkbench();
    expect(component).toBeDefined();
  });

  it("exports datasets including adverse-events", () => {
    expect(safetyWorkbenchDatasets.length).toBeGreaterThan(0);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement audit-trail-viewer stub**

```typescript
// src/stubs/audit-trail-viewer.ts
import { table, lookup } from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";

export function auditTrailStub(datasetUuid: string): Component {
  return table({
    title: "Audit Trail",
    lookup: lookup(datasetUuid),
    sortable: true,
    pageSize: 25,
    columns: [
      { id: "timestamp" as never, name: "Timestamp" },
      { id: "entryType" as never, name: "Type" },
      { id: "actorId" as never, name: "Actor" },
      { id: "subjectId" as never, name: "Subject" },
      { id: "digest" as never, name: "Digest", expression: 'value ? value.substring(0, 16) + "..." : ""' },
    ],
  });
}
```

- [ ] **Step 4: Implement safety-workbench.ts**

```typescript
// src/views/safety-workbench.ts
import {
  rows, columns, table, tabs, panel, markdown, metric, html,
  lookup, groupBy, filterBy, col, count,
} from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import { adverseEventsDs, aePrecedentsDs, ledgerEntriesDs } from "../datasets.js";
import { auditTrailStub } from "../stubs/audit-trail-viewer.js";
import { TRIAL_ID } from "../datasets.js";

export function safetyWorkbench(): Component {
  const aeTable = table({
    title: "Adverse Events",
    lookup: lookup(adverseEventsDs.uuid),
    sortable: true,
    pageSize: 25,
    columns: [
      { id: "grade" as never, name: "Grade", expression: 'value === "GRADE_4" || value === "GRADE_5" ? "🔴 " + value : value === "GRADE_3" ? "🟠 " + value : value' },
      { id: "eventType" as never, name: "Event Type" },
      { id: "patientId" as never, name: "Patient", expression: 'value ? value.substring(0, 8) + "..." : ""' },
      { id: "siteName" as never, name: "Site" },
      { id: "slaTimeRemainingHours" as never, name: "SLA Remaining", expression: 'value < 0 ? "🔴 OVERDUE" : value < 4 ? "🟠 " + Math.round(value) + "h" : value < 12 ? "🟡 " + Math.round(value) + "h" : "🟢 " + Math.round(value) + "h"' },
      { id: "escalationStatus" as never, name: "Escalation" },
      { id: "indStatus" as never, name: "IND Status" },
    ],
    rowStyle: [
      { condition: 'grade === "GRADE_4" || grade === "GRADE_5"', style: { "background-color": "var(--pages-red-2, #fdf0f0)" } },
      { condition: "slaTimeRemainingHours < 0", style: { "background-color": "var(--pages-red-3, #fde0e0)" } },
    ],
    filter: { enabled: true },
    emptyMessage: "No adverse events reported",
  });

  const detailTabs = tabs(
    ["Overview", panel("AE Overview",
      markdown("Select an adverse event from the list to view details."),
    )],
    ["SUSAR Evaluation", panel("SUSAR Evaluation",
      markdown("SUSAR criteria assessment and approval gate."),
      html(`<approval-gate
        endpoint="/demo/adverse-events/{aeId}/approve-susar-gate"
        prompt="Review SUSAR determination for this adverse event"
        context-text="Grade 4+ unexpected suspected adverse reaction — SUSAR criteria evaluation"
        require-confirmation
      ></approval-gate>`),
    )],
    ["Trust & Attestation", panel("Trust Feedback",
      html(`<trust-feedback-display></trust-feedback-display>`),
    )],
    ["Regulatory", panel("Regulatory Status",
      html(`<sla-breach-policy-indicator></sla-breach-policy-indicator>`),
    )],
    ["Precedents", panel("Similar Past Cases",
      html(`<cbr-precedents-panel
        endpoint="/api/trials/${TRIAL_ID}/adverse-events/ae-demo-001/precedents"
        empty-message="No similar adverse events found in case memory"
      ></cbr-precedents-panel>`),
    )],
    ["Audit Trail", panel("Ledger Entries",
      auditTrailStub(ledgerEntriesDs.uuid),
    )],
  );

  return columns([5, 7],
    [aeTable],
    [detailTabs],
  );
}

export const safetyWorkbenchDatasets = [adverseEventsDs, aePrecedentsDs, ledgerEntriesDs];
```

- [ ] **Step 5: Run tests and verify they pass**

- [ ] **Step 6: Commit**

```
feat(#121): safety workbench — AE list + 6 detail tabs

Refs #121
```

---

### Task 6: Protocol Workbench View

**Files:**
- Modify: `runtime/src/main/resources/webui/src/views/protocol-workbench.ts`
- Test: `runtime/src/main/resources/webui/src/__tests__/protocol-workbench.test.ts`

**Interfaces:**
- Consumes: `deviationsDs`, `deviationPrecedentsDs`, `ledgerEntriesDs` from datasets. `<approval-gate>`, `<sla-indicator>` from blocks-ui. `<commitment-lifecycle>`, `<cbr-precedents-panel>` from Task 2-3.
- Produces: `protocolWorkbench(): Component`, `protocolWorkbenchDatasets: DatasetDef[]`

- [ ] **Step 1: Write failing test**

```typescript
// src/__tests__/protocol-workbench.test.ts
import { describe, it, expect } from "vitest";
import { protocolWorkbench, protocolWorkbenchDatasets } from "../views/protocol-workbench.js";

describe("protocol-workbench view", () => {
  it("returns a defined component", () => {
    expect(protocolWorkbench()).toBeDefined();
  });

  it("exports datasets including deviations", () => {
    expect(protocolWorkbenchDatasets.length).toBeGreaterThan(0);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement protocol-workbench.ts**

```typescript
// src/views/protocol-workbench.ts
import {
  rows, columns, table, tabs, panel, markdown, html,
  lookup, groupBy, filterBy, col, count,
} from "@casehubio/pages-ui";
import type { Component } from "@casehubio/pages-ui";
import { deviationsDs, deviationPrecedentsDs, ledgerEntriesDs, TRIAL_ID } from "../datasets.js";
import { auditTrailStub } from "../stubs/audit-trail-viewer.js";

export function protocolWorkbench(): Component {
  const deviationTable = table({
    title: "Protocol Deviations",
    lookup: lookup(deviationsDs.uuid),
    sortable: true,
    pageSize: 25,
    columns: [
      { id: "deviationType" as never, name: "Type" },
      { id: "severity" as never, name: "Severity", expression: 'value === "CRITICAL" ? "🔴 CRITICAL" : value === "MAJOR" ? "🟠 MAJOR" : "🟡 MINOR"' },
      { id: "siteName" as never, name: "Site" },
      { id: "piApprovalStatus" as never, name: "PI Approval", expression: 'value === "COMMANDED" ? "⏳ COMMANDED" : value === "APPROVED" ? "✅ APPROVED" : value === "DECLINED" ? "❌ DECLINED" : value === "EXPIRED" ? "⏰ EXPIRED" : value' },
      { id: "irbDecision" as never, name: "IRB Decision", expression: 'value === "APPROVED" ? "✅ APPROVED" : value === "REJECTED" ? "❌ REJECTED" : value === "PENDING" ? "⏳ PENDING" : value ?? "—"' },
      { id: "reportedAt" as never, name: "Reported", expression: 'value ? new Date(value).toLocaleDateString() : ""' },
    ],
    rowStyle: [
      { condition: 'severity === "CRITICAL"', style: { "background-color": "var(--pages-red-2, #fdf0f0)" } },
    ],
    filter: { enabled: true },
    emptyMessage: "No protocol deviations recorded",
  });

  const detailTabs = tabs(
    ["Overview", panel("Deviation Overview",
      markdown("Select a protocol deviation from the list to view details."),
    )],
    ["PI Commitment", panel("PI Commitment Lifecycle",
      html(`<commitment-lifecycle
        endpoint="/api/trials/${TRIAL_ID}/deviations/{devId}/commitment"
      ></commitment-lifecycle>`),
    )],
    ["IRB Review", panel("IRB Review",
      markdown("IRB committee review status."),
      html(`<approval-gate
        endpoint="/demo/deviations/{deviationId}/approve-irb"
        prompt="Review protocol deviation for IRB approval"
        context-text="CRITICAL protocol deviation requires ethics committee review — 72h deadline"
        require-confirmation
      ></approval-gate>`),
    )],
    ["Precedents", panel("Similar Past Deviations",
      html(`<cbr-precedents-panel
        endpoint="/api/trials/${TRIAL_ID}/deviations/dev-demo-001/precedents"
        empty-message="No similar deviations found in case memory"
      ></cbr-precedents-panel>`),
    )],
    ["Audit Trail", panel("Ledger Entries",
      auditTrailStub(ledgerEntriesDs.uuid),
    )],
  );

  return columns([5, 7],
    [deviationTable],
    [detailTabs],
  );
}

export const protocolWorkbenchDatasets = [deviationsDs, deviationPrecedentsDs, ledgerEntriesDs];
```

- [ ] **Step 4: Run tests and verify they pass**

- [ ] **Step 5: Commit**

```
feat(#121): protocol workbench — deviation list + 5 detail tabs

Refs #121
```

---

### Task 7: Operations View

**Files:**
- Modify: `runtime/src/main/resources/webui/src/views/operations.ts`
- Test: `runtime/src/main/resources/webui/src/__tests__/operations.test.ts`

**Interfaces:**
- Consumes: `trialSummaryDs`, `sitesDs`, `agentsDs`, `ledgerEntriesDs`, `patientsDs`, `workItemsDs` from datasets. `<kpi-metric-row>` from blocks-ui. `<regulatory-compliance-summary>`, `<gdpr-erasure-action>` from Task 3.
- Produces: `operations(): Component`, `operationsDatasets: DatasetDef[]`

- [ ] **Step 1: Write failing test**

```typescript
// src/__tests__/operations.test.ts
import { describe, it, expect } from "vitest";
import { operations, operationsDatasets } from "../views/operations.js";

describe("operations view", () => {
  it("returns a defined component", () => {
    expect(operations()).toBeDefined();
  });

  it("exports datasets including trial-summary and agents", () => {
    expect(operationsDatasets.length).toBeGreaterThanOrEqual(4);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement operations.ts**

```typescript
// src/views/operations.ts
import {
  rows, columns, tabs, panel, table, metric, barChart, pieChart, markdown, html,
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
      endpoint="/api/gdpr/erasure/patients/{subjectId}"
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
```

- [ ] **Step 4: Run tests and verify they pass**

- [ ] **Step 5: Commit**

```
feat(#121): operations view — 5 dashboard tabs

Refs #121
```

---

### Task 8: Commitment Lifecycle Endpoint (Java)

**Files:**
- Modify: `runtime/src/main/java/io/casehub/clinical/resource/TrialDashboardResource.java`
- Test: `runtime/src/test/java/io/casehub/clinical/resource/CommitmentEndpointTest.java`

**Interfaces:**
- Consumes: `CommitmentStore` from qhorus, `ProtocolDeviation` entity, `ChannelService` from qhorus
- Produces: `GET /api/trials/{trialId}/deviations/{devId}/commitment` → `CommitmentLifecycleResponse`

- [ ] **Step 1: Explore existing TrialDashboardResource structure**

Use `ide_file_structure` on `TrialDashboardResource.java` to understand existing endpoint pattern. Use `ide_find_class` to locate `CommitmentStore`, `CommitmentEntity`, and `ChannelService` in qhorus.

- [ ] **Step 2: Write failing test**

```java
// runtime/src/test/java/io/casehub/clinical/resource/CommitmentEndpointTest.java
@QuarkusTest
@TestSecurity(user = "test-actor", roles = { ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR })
public class CommitmentEndpointTest {

    @Inject FixedCurrentPrincipal principal;

    private UUID trialId;
    private UUID deviationId;

    @BeforeEach
    @Transactional
    void setup() {
        ClinicalTrial trial = new ClinicalTrial();
        trial.protocolId = "TEST-COMMITMENT";
        trial.phase = "PHASE_III";
        trial.sponsor = "Test Sponsor";
        trial.tenantId = principal.tenancyId();
        trial.persist();
        trialId = trial.id;

        TrialSite site = new TrialSite();
        site.trial = trial;
        site.siteId = "site-test";
        site.investigatorId = "pi-test";
        site.tenantId = principal.tenancyId();
        site.persist();

        ProtocolDeviation dev = new ProtocolDeviation();
        dev.site = site;
        dev.deviationType = "DOSING_ERROR";
        dev.severity = "CRITICAL";
        dev.tenantId = principal.tenancyId();
        dev.persist();
        deviationId = dev.id;
    }

    @Test
    void returns404WhenDeviationNotFound() {
        given()
            .when().get("/api/trials/{trialId}/deviations/{devId}/commitment",
                trialId, UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    @Test
    void returns200WithCommitmentData() {
        given()
            .when().get("/api/trials/{trialId}/deviations/{devId}/commitment",
                trialId, deviationId)
            .then()
            .statusCode(200)
            .body("deviationId", is(deviationId.toString()));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn install -pl api --batch-mode && mvn test -pl runtime -Dtest=CommitmentEndpointTest --batch-mode`
Expected: FAIL — endpoint not found (404 for all paths)

- [ ] **Step 4: Implement commitment endpoint**

Add to `TrialDashboardResource.java`:

```java
@GET
@Path("/{trialId}/deviations/{devId}/commitment")
@RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
public Response getCommitmentLifecycle(
        @PathParam("trialId") UUID trialId,
        @PathParam("devId") UUID devId) {

    ProtocolDeviation deviation = ProtocolDeviation.findById(devId);
    if (deviation == null || !deviation.site.trial.id.equals(trialId)) {
        return Response.status(404).build();
    }

    var response = new CommitmentLifecycleResponse(
        devId,
        deviation.deviationType,
        deviation.severity,
        deviation.piApprovalStatus,
        deviation.piCommandChannelName,
        deviation.commandedAt,
        deviation.resolvedAt
    );

    return Response.ok(response).build();
}

public record CommitmentLifecycleResponse(
    UUID deviationId,
    String deviationType,
    String severity,
    String piApprovalStatus,
    String channelName,
    Instant commandedAt,
    Instant resolvedAt
) {}
```

- [ ] **Step 5: Run test and verify it passes**

Run: `mvn install -pl api --batch-mode && mvn test -pl runtime -Dtest=CommitmentEndpointTest --batch-mode`
Expected: PASS

- [ ] **Step 6: Commit**

```
feat(#121): commitment lifecycle endpoint for PI Commitment tab

Refs #121
```

---

### Task 9: Cleanup — Delete Old Files, Register Components

**Files:**
- Delete: `runtime/src/main/resources/webui/src/theme.ts`
- Delete: `runtime/src/main/resources/webui/src/helpers.ts`
- Delete: `runtime/src/main/resources/webui/src/dashboard.ts`
- Delete: `runtime/src/main/resources/webui/src/narrative.ts`
- Delete: `runtime/src/main/resources/webui/src/components/clinical-pi-approval.ts`
- Delete: `runtime/src/main/resources/webui/src/components/clinical-susar-gate.ts`
- Delete: `runtime/src/main/resources/webui/src/components/clinical-merkle-verify.ts`
- Delete: `runtime/src/main/resources/webui/src/guided/` (entire directory)
- Delete: `runtime/src/main/resources/webui/src/explore/` (entire directory)

**Interfaces:**
- Consumes: all tasks complete
- Produces: clean file tree matching spec §File Structure

- [ ] **Step 1: Delete old component files**

Remove the 3 custom HTMLElement components replaced by blocks-ui:
- `src/components/clinical-pi-approval.ts` → replaced by `<approval-gate>`
- `src/components/clinical-susar-gate.ts` → replaced by `<approval-gate>`
- `src/components/clinical-merkle-verify.ts` → replaced by audit-trail-viewer stub

- [ ] **Step 2: Delete old infrastructure files**

- `src/theme.ts` → replaced by `pages-ui-tokens`
- `src/helpers.ts` → `actionButton()` / `alert()` no longer needed
- `src/dashboard.ts` → replaced by `app.ts`
- `src/narrative.ts` → guided mode deferred

- [ ] **Step 3: Delete guided and explore directories**

- `src/guided/` — 8 step files. Content absorbed into workbench views. Git history preserves them.
- `src/explore/` — 6 page files. Content absorbed into workbench + operations views.

- [ ] **Step 4: Verify build still succeeds**

Run: `cd runtime/src/main/resources/webui && npm run build && npx vitest run`
Expected: Build succeeds, all tests pass, no import errors from deleted files

- [ ] **Step 5: Verify file tree matches spec**

```
src/
├── index.ts
├── app.ts
├── datasets.ts
├── mock/ (10 CSV files)
├── stubs/
│   └── audit-trail-viewer.ts
├── views/
│   ├── work-queue.ts
│   ├── safety-workbench.ts
│   ├── protocol-workbench.ts
│   └── operations.ts
├── components/
│   ├── commitment-lifecycle.ts
│   ├── cbr-precedents-panel.ts
│   ├── trust-feedback-display.ts
│   ├── regulatory-compliance-summary.ts
│   ├── gdpr-erasure-action.ts
│   └── sla-breach-policy-indicator.ts
└── __tests__/ (test files)
```

- [ ] **Step 6: Commit**

```
feat(#121): cleanup — remove old guided/explore/custom component files

Refs #121
```

---

## Deferred Items (tracked as issues)

These are explicitly out of scope per the spec:

| Item | Tracking |
|------|----------|
| Guided mode overlay / second window | Separate concern — no issue yet, create when designing |
| `<audit-trail-viewer>` swap-in | blocks-ui#9 |
| `<trust-score-panel>` swap-in | blocks-ui#11 |
| `<case-timeline>` swap-in | blocks-ui#10 |
| `<case-workbench>` swap-in | Blocked on AML promotion (aml#91) |
| Deep linking to entities | Needs casehub-pages enhancement |
| WorkItemResponse `types` field | blocks-ui#42 |
| Temporal dataset simulation | aml#101 |
| `dualDataset()` in pages-ui | Consider after clinical + AML validate the pattern |
