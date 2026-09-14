import { LitElement, html, css } from "lit";
import { property, state } from "lit/decorators.js";
import { onTableSelection } from "../selection-bridge.js";
import { createEventConnection, type EventConnection } from "@casehubio/pages-data";

interface CascadeEvent {
  readonly step: string;
  readonly status: string;
  readonly timestamp: string | null;
  readonly actor: string | null;
  readonly detail: string | null;
  readonly data: Record<string, unknown>;
}

const STEP_LABELS: Record<string, string> = {
  AE_REPORTED: "Adverse Event Reported",
  SLA_ASSIGNED: "SLA Deadline Assigned",
  ESCALATION_CASE_STARTED: "Escalation Case Started",
  AGENT_SELECTED: "Agent Selected",
  AGENT_REASONING: "Agent Reasoning",
  AGENT_RESULT: "Agent Result",
  GATE_OPENED: "Oversight Gate Opened",
  GATE_RESOLVED: "Oversight Gate Resolved",
  SAFETY_OFFICER_NOTIFIED: "Safety Officer Notified",
  REGULATORY_SUBMISSION_STARTED: "IND Submission Started",
  LEDGER_SEALED: "Merkle Entry Sealed",
};

const STEP_CATEGORIES: Record<string, string> = {
  AE_REPORTED: "lifecycle",
  SLA_ASSIGNED: "lifecycle",
  ESCALATION_CASE_STARTED: "lifecycle",
  AGENT_SELECTED: "agent",
  AGENT_REASONING: "agent",
  AGENT_RESULT: "agent",
  GATE_OPENED: "gate",
  GATE_RESOLVED: "gate",
  SAFETY_OFFICER_NOTIFIED: "lifecycle",
  REGULATORY_SUBMISSION_STARTED: "lifecycle",
  LEDGER_SEALED: "audit",
};

const CATEGORY_STYLES: Record<string, string> = {
  lifecycle:
    "background: var(--pages-accent-3, #dbeafe); color: var(--pages-accent-11, #1e40af)",
  agent:
    "background: var(--pages-warning-3, #fef3c7); color: var(--pages-warning-11, #92400e)",
  gate:
    "background: var(--pages-error-3, #fee2e2); color: var(--pages-error-11, #991b1b)",
  audit:
    "background: var(--pages-success-3, #dcfce7); color: var(--pages-success-11, #166534)",
};

const STATUS_ICONS: Record<string, string> = {
  COMPLETED: "✅",
  ACTIVE: "⏳",
  PENDING: "⚪",
  FAILED: "❌",
  SKIPPED: "⏭️",
};

const TERMINAL = new Set(["COMPLETED", "FAILED", "SKIPPED"]);

export class ClinicalCascadeTimeline extends LitElement {
  static styles = css`
    :host {
      display: block;
      font-family: var(--pages-font-family, sans-serif);
    }
    .empty {
      color: var(--pages-neutral-9, #95a5a6);
      font-style: italic;
      padding: var(--pages-space-4, 1rem);
    }
    .timeline {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      padding: 0.5rem;
    }
    .step {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      padding: 0.75rem;
      border-radius: 8px;
      border: 1px solid var(--pages-neutral-4, #eee);
      transition: border-color 0.3s, background 0.3s;
    }
    .step--active {
      border-color: var(--pages-accent-7, #60a5fa);
      background: var(--pages-accent-2, #eff6ff);
    }
    .step--failed {
      border-color: var(--pages-error-7, #ef4444);
      background: var(--pages-error-2, #fef2f2);
    }
    .icon {
      font-size: 1.25rem;
      min-width: 1.5rem;
      text-align: center;
    }
    .content {
      flex: 1;
    }
    .label {
      font-weight: 600;
      font-size: 14px;
    }
    .detail {
      font-size: 12px;
      color: var(--pages-neutral-9);
      margin-top: 2px;
    }
    .category {
      display: inline-block;
      padding: 1px 6px;
      border-radius: 3px;
      font-size: 11px;
      font-weight: 500;
      margin-left: 0.5rem;
    }
    .meta {
      font-size: 11px;
      color: var(--pages-neutral-8);
      margin-top: 4px;
    }
    .status-bar {
      padding: 0.5rem;
      font-size: 12px;
      border-bottom: 1px solid var(--pages-neutral-4);
    }
    .status-bar--connected {
      color: var(--pages-success-9, #16a34a);
    }
    .status-bar--reconnecting {
      color: var(--pages-warning-9, #ca8a04);
    }
    .status-bar--disconnected {
      color: var(--pages-neutral-8, #737373);
    }
  `;

  @property({ attribute: "data-source-dataset" }) sourceDataset = "";
  @state() private _aeId = "";
  @state() private _steps: CascadeEvent[] = [];
  @state() private _loading = false;
  @state() private _connectionStatus = "disconnected";
  private _connection: EventConnection | null = null;

  connectedCallback() {
    super.connectedCallback();
    onTableSelection(
      this,
      this.sourceDataset,
      (row: Record<string, unknown>) => {
        const id = row?.id as string;
        if (id && id !== this._aeId) {
          this._aeId = id;
          this._loadCascade(id);
        }
      },
    );
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    this._disconnect();
  }

  private async _loadCascade(aeId: string) {
    this._disconnect();
    this._loading = true;
    this._steps = [];

    try {
      const resp = await fetch(`/api/adverse-events/${aeId}/cascade`);
      if (!resp.ok) {
        this._steps = [];
        return;
      }
      this._steps = (await resp.json()) as CascadeEvent[];
      this._subscribe(aeId);
    } catch (e) {
      console.warn("[CascadeTimeline] fetch failed:", e);
    } finally {
      this._loading = false;
    }
  }

  private _subscribe(aeId: string) {
    const wsUrl = `ws://${window.location.host}/ws/push`;
    const topic = `clinical:ae:${aeId}:cascade`;
    const target = new EventTarget();

    target.addEventListener("pages-event", ((e: CustomEvent) => {
      const payload = e.detail?.payload as CascadeEvent | undefined;
      if (!payload?.step) return;
      this._mergeEvent(payload);
    }) as EventListener);

    this._connection = createEventConnection(wsUrl, {
      config: { eventTarget: target },
      onStatusChange: (status) => {
        this._connectionStatus = status;
      },
    });
    this._connection.listen([topic]);
  }

  private _mergeEvent(event: CascadeEvent) {
    const idx = this._steps.findIndex((s) => s.step === event.step);
    if (idx < 0) return;
    const existing = this._steps[idx]!;
    if (TERMINAL.has(existing.status)) return;
    const updated = [...this._steps];
    updated[idx] = event;
    this._steps = updated;
  }

  private _disconnect() {
    this._connection?.close();
    this._connection = null;
    this._connectionStatus = "disconnected";
  }

  render() {
    if (!this._aeId) {
      return html`<p class="empty">
        Select an adverse event to view its cascade.
      </p>`;
    }
    if (this._loading) {
      return html`<p class="empty">Loading cascade...</p>`;
    }

    return html`
      <div class="status-bar status-bar--${this._connectionStatus}">
        ${this._connectionStatus === "connected"
          ? "● Live"
          : this._connectionStatus}
      </div>
      <div class="timeline">
        ${this._steps.map((step) => {
          const cat = STEP_CATEGORIES[step.step] ?? "lifecycle";
          const catStyle =
            CATEGORY_STYLES[cat] ?? CATEGORY_STYLES.lifecycle;
          const stepClass =
            step.status === "ACTIVE"
              ? "step step--active"
              : step.status === "FAILED"
                ? "step step--failed"
                : "step";
          return html`
            <div class="${stepClass}">
              <span class="icon"
                >${STATUS_ICONS[step.status] ?? "⚪"}</span
              >
              <div class="content">
                <span class="label"
                  >${STEP_LABELS[step.step] ?? step.step}</span
                >
                <span class="category" style="${catStyle}">${cat}</span>
                ${step.detail
                  ? html`<div class="detail">${step.detail}</div>`
                  : ""}
                ${step.actor
                  ? html`<div class="meta">Actor: ${step.actor}</div>`
                  : ""}
                ${step.timestamp
                  ? html`<div class="meta">
                      ${new Date(step.timestamp).toLocaleTimeString()}
                    </div>`
                  : ""}
              </div>
            </div>
          `;
        })}
      </div>
    `;
  }
}
