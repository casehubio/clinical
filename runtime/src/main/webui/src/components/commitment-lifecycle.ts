import { LitElement, html, css } from "lit";
import { property, state } from "lit/decorators.js";
import { emitPagesEvent } from "@casehubio/pages-component";
import { onTableSelection } from "../selection-bridge.js";

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
  private _unsub?: () => void;

  connectedCallback() {
    super.connectedCallback();
    if (this.commitmentId) this._fetchCommitment();
    const ds = this.getAttribute("data-source-dataset");
    if (ds) {
      this._unsub = onTableSelection(this, ds, (entityId, trialId) => {
        this.commitmentId = entityId;
        this.endpoint = `/api/trials/${trialId}/deviations/${entityId}/commitment`;
      });
    }
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    this._unsub?.();
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
      const commitment: CommitmentState = await res.json();
      this._commitment = commitment;
      emitPagesEvent(this, "commitment.stage-changed", {
        commitmentId: this.commitmentId,
        currentStage: commitment.currentStage,
      });
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
