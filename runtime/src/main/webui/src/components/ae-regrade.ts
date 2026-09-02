import { LitElement, html, css } from "lit";
import { property, state } from "lit/decorators.js";
import { onTableSelection } from "../selection-bridge.js";

const GRADES = ["GRADE_1", "GRADE_2", "GRADE_3", "GRADE_4", "GRADE_5"];

export class ClinicalAeRegrade extends LitElement {
  static styles = css`
    :host { display: block; font-family: var(--pages-font-family, sans-serif); padding: var(--pages-space-4, 1rem); }
    .form-group { margin-bottom: var(--pages-space-4, 1rem); }
    label { display: block; font-weight: 600; margin-bottom: var(--pages-space-2, 0.5rem); }
    select, textarea { width: 100%; padding: var(--pages-space-2, 0.5rem); border: 1px solid var(--pages-neutral-6, #bdc3c7); border-radius: var(--pages-radius-2, 4px); font-size: 14px; font-family: inherit; box-sizing: border-box; }
    textarea { min-height: 80px; resize: vertical; }
    button { padding: var(--pages-space-2, 0.5rem) var(--pages-space-4, 1rem); background: var(--pages-accent-9, #3498db); color: white; border: none; border-radius: var(--pages-radius-2, 4px); font-size: 14px; cursor: pointer; }
    button:disabled { opacity: 0.5; cursor: not-allowed; }
    .success { color: var(--pages-green-9, #27ae60); padding: var(--pages-space-3, 0.75rem); }
    .error { color: var(--pages-red-9, #e74c3c); padding: var(--pages-space-3, 0.75rem); }
  `;

  @property() endpoint = "";
  @property({ type: Boolean }) demo = false;
  @state() private _grade = "";
  @state() private _reason = "";
  @state() private _submitting = false;
  @state() private _result: "success" | "error" | null = null;
  private _unsub?: () => void;

  connectedCallback() {
    super.connectedCallback();
    const ds = this.getAttribute("data-source-dataset");
    if (ds) {
      this._unsub = onTableSelection(this, ds, (entityId, trialId) => {
        this.endpoint = `/api/trials/${trialId}/adverse-events/${entityId}/regrade`;
      });
    }
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    this._unsub?.();
  }

  private async _submit() {
    if (!this._grade || !this._reason.trim()) return;
    if (this.demo) {
      this._result = "success";
      this.dispatchEvent(new CustomEvent("pages-regrade-completed", { bubbles: true, composed: true }));
      return;
    }
    this._submitting = true;
    this._result = null;
    try {
      const res = await fetch(this.endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ grade: this._grade, reason: this._reason }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      this._result = "success";
      this._grade = "";
      this._reason = "";
      this.dispatchEvent(new CustomEvent("pages-regrade-completed", { bubbles: true, composed: true }));
    } catch {
      this._result = "error";
    } finally {
      this._submitting = false;
    }
  }

  render() {
    return html`
      <div class="form-group">
        <label>New Grade</label>
        <select .value=${this._grade} @change=${(e: Event) => { this._grade = (e.target as HTMLSelectElement).value; this._result = null; }}>
          <option value="">Select grade...</option>
          ${GRADES.map(g => html`<option value=${g}>${g.replace("GRADE_", "Grade ")}</option>`)}
        </select>
      </div>
      <div class="form-group">
        <label>Reason (required)</label>
        <textarea .value=${this._reason} @input=${(e: Event) => { this._reason = (e.target as HTMLTextAreaElement).value; this._result = null; }} maxlength="500" placeholder="Clinical justification for grade change..."></textarea>
      </div>
      <button ?disabled=${!this._grade || !this._reason.trim() || this._submitting} @click=${this._submit}>
        ${this._submitting ? "Submitting..." : "Submit Regrade"}
      </button>
      ${this._result === "success" ? html`<div class="success">Grade updated successfully.</div>` : ""}
      ${this._result === "error" ? html`<div class="error">Failed to submit regrade. Check the console.</div>` : ""}
    `;
  }
}
