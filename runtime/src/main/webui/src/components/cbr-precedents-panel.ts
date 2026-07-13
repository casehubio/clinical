import { LitElement, html, css } from "lit";
import { property, state } from "lit/decorators.js";
import { emitPagesEvent } from "@casehubio/pages-component";

export interface ColumnDef {
  readonly key: string;
  readonly label: string;
}

interface Precedent {
  readonly caseId: string;
  readonly similarity: number;
  readonly outcome: string;
  readonly resolutionTime: string;
  [key: string]: unknown;
}

export class ClinicalCbrPrecedentsPanel extends LitElement {
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
    .table-container {
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 14px;
    }
    th {
      background: var(--pages-neutral-3, #ecf0f1);
      padding: var(--pages-space-3, 0.75rem);
      text-align: left;
      font-weight: 600;
      border-bottom: 2px solid var(--pages-neutral-6, #bdc3c7);
    }
    td {
      padding: var(--pages-space-3, 0.75rem);
      border-bottom: 1px solid var(--pages-neutral-4, #eee);
    }
    tr:hover {
      background: var(--pages-neutral-2, #f8f9fa);
      cursor: pointer;
    }
    .similarity-bar {
      display: flex;
      align-items: center;
      gap: var(--pages-space-2, 0.5rem);
    }
    .bar-bg {
      flex: 1;
      height: 8px;
      background: var(--pages-neutral-4, #eee);
      border-radius: 4px;
      overflow: hidden;
    }
    .bar-fill {
      height: 100%;
      background: var(--pages-accent-9, #3498db);
    }
    .percentage {
      font-weight: 600;
      min-width: 45px;
    }
    .outcome-badge {
      display: inline-block;
      padding: 4px 8px;
      border-radius: var(--pages-radius-2, 4px);
      font-size: 12px;
      font-weight: 500;
    }
    .outcome-badge--resolved {
      background: var(--pages-green-3, #d4edda);
      color: var(--pages-green-11, #155724);
    }
    .outcome-badge--pending {
      background: var(--pages-yellow-3, #fff3cd);
      color: var(--pages-yellow-11, #856404);
    }
    .outcome-badge--escalated {
      background: var(--pages-orange-3, #ffe5d0);
      color: var(--pages-orange-11, #8a4000);
    }
  `;

  @property() endpoint = "";
  @property({ type: Boolean }) demo = false;
  @property({ attribute: false }) data: Precedent[] | null = null;
  @property({ attribute: false }) columns: ColumnDef[] = [
    { key: "similarity", label: "Similarity" },
    { key: "grade", label: "Grade/Severity" },
    { key: "outcome", label: "Outcome" },
    { key: "resolutionTime", label: "Resolution Time" },
    { key: "reportedDate", label: "Reported" },
  ];
  @property() emptyMessage = "No similar cases found";

  @state() private _precedents: Precedent[] = [];
  @state() private _loading = false;
  @state() private _error = "";

  connectedCallback() {
    super.connectedCallback();
    if (this.data) {
      this._precedents = this.data;
    } else if (this.demo) {
      this._precedents = this._getDemoData();
    } else if (this.endpoint) {
      this._fetchPrecedents();
    }
  }

  updated(changed: Map<string, unknown>) {
    if (changed.has("data") && this.data) {
      this._precedents = this.data;
      this._error = "";
      this._loading = false;
    } else if (changed.has("demo") && this.demo && !this.data) {
      this._precedents = this._getDemoData();
      this._error = "";
      this._loading = false;
    } else if (changed.has("endpoint") && this.endpoint && !this.data && !this.demo) {
      this._fetchPrecedents();
    }
  }

  private _getDemoData(): Precedent[] {
    return [
      { caseId: "prec-001", similarity: 92, outcome: "Resolved", resolutionTime: "3 days", grade: "GRADE_4", reportedDate: "2026-05-12" },
      { caseId: "prec-002", similarity: 85, outcome: "Escalated", resolutionTime: "5 days", grade: "GRADE_3", reportedDate: "2026-04-28" },
      { caseId: "prec-003", similarity: 78, outcome: "Resolved", resolutionTime: "2 days", grade: "GRADE_4", reportedDate: "2026-03-15" },
    ];
  }

  private async _fetchPrecedents() {
    this._loading = true;
    this._error = "";
    try {
      const res = await fetch(this.endpoint);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      this._precedents = await res.json();
    } catch (e) {
      this._error = e instanceof Error ? e.message : "Failed to load";
    } finally {
      this._loading = false;
    }
  }

  private _handleRowClick(precedent: Precedent) {
    emitPagesEvent(this, "precedent.selected", {
      caseId: precedent.caseId,
      similarity: precedent.similarity,
      outcome: precedent.outcome,
    });
  }

  private _getOutcomeClass(outcome: string): string {
    const lower = outcome.toLowerCase();
    if (lower.includes("resolved")) return "outcome-badge--resolved";
    if (lower.includes("pending")) return "outcome-badge--pending";
    if (lower.includes("escalated")) return "outcome-badge--escalated";
    return "";
  }

  render() {
    if (this._loading) return html`<div class="empty">Loading precedents...</div>`;
    if (this._error) return html`<div class="empty">Failed to load precedents</div>`;
    if (!this._precedents.length) return html`<div class="empty">${this.emptyMessage}</div>`;

    return html`
      <div class="table-container">
        <table>
          <thead>
            <tr>
              ${this.columns.map(col => html`<th>${col.label}</th>`)}
            </tr>
          </thead>
          <tbody>
            ${this._precedents.map(
              p => html`
                <tr @click=${() => this._handleRowClick(p)}>
                  ${this.columns.map(col => {
                    if (col.key === "similarity") {
                      return html`
                        <td>
                          <div class="similarity-bar">
                            <div class="bar-bg">
                              <div class="bar-fill" style="width: ${p.similarity}%"></div>
                            </div>
                            <span class="percentage">${p.similarity}%</span>
                          </div>
                        </td>
                      `;
                    } else if (col.key === "outcome") {
                      return html`
                        <td>
                          <span class="outcome-badge ${this._getOutcomeClass(String(p.outcome))}">
                            ${p.outcome}
                          </span>
                        </td>
                      `;
                    } else {
                      return html`<td>${p[col.key]}</td>`;
                    }
                  })}
                </tr>
              `
            )}
          </tbody>
        </table>
      </div>
    `;
  }
}
