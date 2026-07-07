import { LitElement, html, css } from "lit";
import { property, state } from "lit/decorators.js";
import { emitPagesEvent } from "@casehubio/pages-component";

interface ErasureReceipt {
  readonly erasureId?: string;
  readonly subjectId: string;
  readonly reason: string;
  readonly status: "WITHDRAWN" | "ALREADY_WITHDRAWN";
  readonly timestamp: string;
  readonly entryCount?: number;
}

export class ClinicalGdprErasureAction extends LitElement {
  static styles = css`
    :host {
      display: block;
      font-family: var(--pages-font-family, sans-serif);
    }
    .form-container {
      border: 1px solid var(--pages-neutral-4, #eee);
      border-radius: var(--pages-radius-3, 8px);
      padding: var(--pages-space-4, 1rem);
      background: white;
      max-width: 500px;
    }
    .form-field {
      margin-bottom: var(--pages-space-3, 0.75rem);
    }
    label {
      display: block;
      font-weight: 600;
      color: var(--pages-neutral-11, #7f8c8d);
      font-size: 13px;
      margin-bottom: var(--pages-space-2, 0.5rem);
    }
    input,
    select {
      width: 100%;
      padding: var(--pages-space-3, 0.75rem);
      border: 1px solid var(--pages-neutral-6, #bdc3c7);
      border-radius: var(--pages-radius-2, 4px);
      font-size: 14px;
      font-family: inherit;
    }
    input:focus,
    select:focus {
      outline: 2px solid var(--pages-accent-9, #3498db);
      border-color: transparent;
    }
    .button-group {
      display: flex;
      gap: var(--pages-space-2, 0.5rem);
      margin-top: var(--pages-space-4, 1rem);
    }
    button {
      padding: var(--pages-space-3, 0.75rem) var(--pages-space-4, 1rem);
      border: none;
      border-radius: var(--pages-radius-2, 4px);
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      font-family: inherit;
    }
    .btn-primary {
      background: var(--pages-red-9, #e74c3c);
      color: white;
    }
    .btn-primary:hover {
      background: var(--pages-red-10, #c0392b);
    }
    .btn-primary:disabled {
      background: var(--pages-neutral-6, #bdc3c7);
      cursor: not-allowed;
    }
    .btn-secondary {
      background: var(--pages-neutral-4, #eee);
      color: var(--pages-neutral-11, #7f8c8d);
    }
    .btn-secondary:hover {
      background: var(--pages-neutral-5, #ddd);
    }
    .receipt {
      border: 1px solid var(--pages-green-6, #27ae60);
      border-radius: var(--pages-radius-3, 8px);
      padding: var(--pages-space-4, 1rem);
      background: var(--pages-green-2, #f0fdf4);
      max-width: 500px;
    }
    .receipt-title {
      font-weight: 600;
      color: var(--pages-green-11, #155724);
      margin-bottom: var(--pages-space-3, 0.75rem);
    }
    .receipt-row {
      display: flex;
      justify-content: space-between;
      padding: var(--pages-space-2, 0.5rem) 0;
      border-bottom: 1px solid var(--pages-green-4, #d4edda);
      font-size: 13px;
    }
    .receipt-row:last-child {
      border-bottom: none;
    }
    .receipt-label {
      font-weight: 600;
      color: var(--pages-green-10, #1e7e34);
    }
    .receipt-value {
      color: var(--pages-green-11, #155724);
    }
    .error {
      color: var(--pages-red-9, #e74c3c);
      font-size: 13px;
      margin-top: var(--pages-space-2, 0.5rem);
    }
    .warning {
      background: var(--pages-yellow-3, #fff3cd);
      border: 1px solid var(--pages-yellow-6, #ffc107);
      border-radius: var(--pages-radius-2, 4px);
      padding: var(--pages-space-3, 0.75rem);
      margin-bottom: var(--pages-space-3, 0.75rem);
      font-size: 13px;
      color: var(--pages-yellow-11, #856404);
    }
  `;

  @property() endpoint = "";
  @property() subjectLabel = "Subject";
  @property({ attribute: false }) reasonOptions: string[] = ["GDPR Art.17 Request", "Data Retention Policy", "Account Deletion"];

  @state() private _subjectId = "";
  @state() private _reason = "";
  @state() private _loading = false;
  @state() private _error = "";
  @state() private _receipt: ErasureReceipt | null = null;
  @state() private _confirmPending = false;

  private _handleSubmit(e: Event) {
    e.preventDefault();
    if (!this._subjectId || !this._reason) {
      this._error = "Subject ID and reason are required";
      return;
    }
    this._confirmPending = true;
  }

  private _cancelConfirm() {
    this._confirmPending = false;
  }

  private _confirmErasure() {
    this._confirmPending = false;
    this._performErasure();
  }

  private async _performErasure() {
    this._loading = true;
    this._error = "";
    try {
      const res = await fetch(this.endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          subjectId: this._subjectId,
          reason: this._reason,
        }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      this._receipt = {
        erasureId: data.erasureId,
        subjectId: this._subjectId,
        reason: this._reason,
        status: data.status || "WITHDRAWN",
        timestamp: data.timestamp || new Date().toISOString(),
        entryCount: data.entryCount,
      };
      emitPagesEvent(this, "gdpr.erasure-completed", {
        subjectId: this._subjectId,
        reason: this._reason,
        status: this._receipt.status,
      });
    } catch (e) {
      this._error = e instanceof Error ? e.message : "Failed to perform erasure";
    } finally {
      this._loading = false;
    }
  }

  private _reset() {
    this._subjectId = "";
    this._reason = "";
    this._receipt = null;
    this._error = "";
  }

  render() {
    if (this._receipt) {
      return html`
        <div class="receipt">
          <div class="receipt-title">
            ${this._receipt.status === "ALREADY_WITHDRAWN" ? "Erasure Already Complete" : "Erasure Complete"}
          </div>
          ${this._receipt.erasureId ? html`
            <div class="receipt-row">
              <span class="receipt-label">Erasure ID</span>
              <span class="receipt-value">${this._receipt.erasureId}</span>
            </div>
          ` : ""}
          <div class="receipt-row">
            <span class="receipt-label">${this.subjectLabel} ID</span>
            <span class="receipt-value">${this._receipt.subjectId}</span>
          </div>
          <div class="receipt-row">
            <span class="receipt-label">Reason</span>
            <span class="receipt-value">${this._receipt.reason}</span>
          </div>
          <div class="receipt-row">
            <span class="receipt-label">Status</span>
            <span class="receipt-value">${this._receipt.status}</span>
          </div>
          ${this._receipt.entryCount != null ? html`
            <div class="receipt-row">
              <span class="receipt-label">Entries Erased</span>
              <span class="receipt-value">${this._receipt.entryCount}</span>
            </div>
          ` : ""}
          <div class="receipt-row">
            <span class="receipt-label">Timestamp</span>
            <span class="receipt-value">${new Date(this._receipt.timestamp).toLocaleString()}</span>
          </div>
          <div style="margin-top: 1rem;">
            <button type="button" class="btn-secondary" @click=${this._reset}>Perform Another Erasure</button>
          </div>
        </div>
      `;
    }

    if (this._confirmPending) {
      return html`
        <div class="form-container">
          <div class="warning" style="background: var(--pages-red-3, #fde0e0); border-color: var(--pages-red-6, #e74c3c); color: var(--pages-red-11, #8b0000);">
            ⚠️ You are about to permanently erase all data for ${this.subjectLabel.toLowerCase()} <strong>${this._subjectId}</strong>. This action cannot be undone.
          </div>
          <div class="receipt-row" style="border-bottom: 1px solid var(--pages-neutral-4, #eee); font-size: 13px;">
            <span style="font-weight: 600;">Reason</span>
            <span>${this._reason}</span>
          </div>
          <div class="button-group">
            <button type="button" class="btn-primary" @click=${this._confirmErasure}>
              Permanently Erase Data
            </button>
            <button type="button" class="btn-secondary" @click=${this._cancelConfirm}>
              Cancel
            </button>
          </div>
        </div>
      `;
    }

    return html`
      <form class="form-container" @submit=${this._handleSubmit}>
        <div class="warning">
          ⚠️ This action is irreversible. All data for the specified ${this.subjectLabel.toLowerCase()} will be permanently erased.
        </div>
        <div class="form-field">
          <label for="subject-id">${this.subjectLabel} ID</label>
          <input
            id="subject-id"
            type="text"
            .value=${this._subjectId}
            @input=${(e: InputEvent) => (this._subjectId = (e.target as HTMLInputElement).value)}
            placeholder="Enter ${this.subjectLabel.toLowerCase()} ID"
            ?disabled=${this._loading}
          />
        </div>
        <div class="form-field">
          <label for="reason">Erasure Reason</label>
          <select
            id="reason"
            .value=${this._reason}
            @change=${(e: Event) => (this._reason = (e.target as HTMLSelectElement).value)}
            ?disabled=${this._loading}
          >
            <option value="">Select a reason</option>
            ${this.reasonOptions.map(opt => html`<option value="${opt}">${opt}</option>`)}
          </select>
        </div>
        ${this._error ? html`<div class="error">${this._error}</div>` : ""}
        <div class="button-group">
          <button type="submit" class="btn-primary" ?disabled=${this._loading}>
            ${this._loading ? "Processing..." : "Confirm Erasure"}
          </button>
        </div>
      </form>
    `;
  }
}
