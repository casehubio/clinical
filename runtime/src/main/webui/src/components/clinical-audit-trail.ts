import { LitElement, html, css } from "lit";
import { property, state } from "lit/decorators.js";

interface LedgerRow {
  readonly occurredAt: string;
  readonly entryType: string;
  readonly actorId: string;
  readonly subjectId: string;
  readonly digest: string;
}

export class ClinicalAuditTrail extends LitElement {
  static styles = css`
    :host { display: block; font-family: var(--pages-font-family, sans-serif); }
    .empty { color: var(--pages-neutral-9, #95a5a6); font-style: italic; padding: var(--pages-space-4, 1rem); }
    table { width: 100%; border-collapse: collapse; font-size: 14px; }
    th { background: var(--pages-neutral-3, #ecf0f1); padding: var(--pages-space-3, 0.75rem); text-align: left; font-weight: 600; border-bottom: 2px solid var(--pages-neutral-6, #bdc3c7); }
    td { padding: var(--pages-space-3, 0.75rem); border-bottom: 1px solid var(--pages-neutral-4, #eee); }
    tr:hover { background: var(--pages-neutral-2, #f8f9fa); }
  `;

  @property({ attribute: "trial-id" }) trialId = "";
  @property({ attribute: "subject-id" }) subjectId = "";
  @state() private _entries: LedgerRow[] = [];
  @state() private _loading = false;
  @state() private _error = "";

  updated(changed: Map<string, unknown>) {
    if ((changed.has("trialId") || changed.has("subjectId")) && this.trialId && this.subjectId) {
      this._fetch();
    }
  }

  private async _fetch() {
    this._loading = true;
    this._error = "";
    try {
      const res = await fetch(`/api/trials/${this.trialId}/ledger-entries`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const all: LedgerRow[] = await res.json();
      this._entries = all.filter(e => e.subjectId === this.subjectId);
    } catch (e) {
      this._error = e instanceof Error ? e.message : "Failed to load";
    } finally {
      this._loading = false;
    }
  }

  render() {
    if (!this.subjectId) return html`<div class="empty">Select an entity to view its audit trail.</div>`;
    if (this._loading) return html`<div class="empty">Loading audit trail...</div>`;
    if (this._error) return html`<div class="empty">Unable to load audit trail.</div>`;
    if (!this._entries.length) return html`<div class="empty">No ledger entries for this entity.</div>`;
    return html`
      <table>
        <thead><tr><th>Timestamp</th><th>Type</th><th>Actor</th><th>Subject</th><th>Digest</th></tr></thead>
        <tbody>
          ${this._entries.map(e => html`
            <tr>
              <td>${e.occurredAt}</td>
              <td>${e.entryType}</td>
              <td>${e.actorId}</td>
              <td>${e.subjectId}</td>
              <td>${e.digest ? e.digest.substring(0, 16) + "..." : ""}</td>
            </tr>
          `)}
        </tbody>
      </table>
    `;
  }
}
