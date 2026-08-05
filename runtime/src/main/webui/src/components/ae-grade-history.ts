import { LitElement, html, css } from "lit";
import { property, state } from "lit/decorators.js";

interface GradeChange {
  readonly id: string;
  readonly previousGrade: string | null;
  readonly newGrade: string;
  readonly changedAt: string;
  readonly changedBy: string;
  readonly reason: string | null;
}

export class ClinicalAeGradeHistory extends LitElement {
  static styles = css`
    :host { display: block; font-family: var(--pages-font-family, sans-serif); }
    .empty { color: var(--pages-neutral-9, #95a5a6); font-style: italic; padding: var(--pages-space-4, 1rem); }
    table { width: 100%; border-collapse: collapse; font-size: 14px; }
    th { background: var(--pages-neutral-3, #ecf0f1); padding: var(--pages-space-3, 0.75rem); text-align: left; font-weight: 600; border-bottom: 2px solid var(--pages-neutral-6, #bdc3c7); }
    td { padding: var(--pages-space-3, 0.75rem); border-bottom: 1px solid var(--pages-neutral-4, #eee); }
    .grade-arrow { font-weight: 600; }
    .grade--high { color: var(--pages-red-9, #e74c3c); }
    .grade--mid { color: var(--pages-orange-9, #e67e22); }
  `;

  @property() endpoint = "";
  @property({ type: Boolean }) demo = false;
  @state() private _history: GradeChange[] = [];
  @state() private _loading = false;
  @state() private _error = "";

  connectedCallback() {
    super.connectedCallback();
    if (this.demo) { this._history = this._getDemoData(); }
    else if (this.endpoint) { this._fetch(); }
    this.addEventListener("pages-regrade-completed", () => { this._fetch(); });
  }

  private _getDemoData(): GradeChange[] {
    return [
      { id: "gc-1", previousGrade: null, newGrade: "GRADE_1", changedAt: "2026-07-10T08:00:00Z", changedBy: "system", reason: "Initial report" },
      { id: "gc-2", previousGrade: "GRADE_1", newGrade: "GRADE_3", changedAt: "2026-07-12T14:30:00Z", changedBy: "dr-smith", reason: "Condition worsened — hepatotoxicity confirmed" },
    ];
  }

  private async _fetch() {
    if (!this.endpoint) return;
    this._loading = true;
    this._error = "";
    try {
      const res = await fetch(this.endpoint);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      this._history = await res.json();
    } catch (e) {
      this._error = e instanceof Error ? e.message : "Failed to load";
    } finally {
      this._loading = false;
    }
  }

  private _gradeClass(grade: string): string {
    if (grade === "GRADE_4" || grade === "GRADE_5") return "grade--high";
    if (grade === "GRADE_3") return "grade--mid";
    return "";
  }

  private _formatDate(iso: string): string {
    try { return new Date(iso).toLocaleString(); } catch { return iso; }
  }

  render() {
    if (this._loading) return html`<div class="empty">Loading grade history...</div>`;
    if (this._error) return html`<div class="empty">Failed to load grade history</div>`;
    if (!this._history.length) return html`<div class="empty">No grade changes recorded</div>`;

    return html`
      <table>
        <thead><tr><th>Timestamp</th><th>Grade Change</th><th>Changed By</th><th>Reason</th></tr></thead>
        <tbody>
          ${this._history.map(gc => html`
            <tr>
              <td>${this._formatDate(gc.changedAt)}</td>
              <td class="grade-arrow">
                <span>${gc.previousGrade ? gc.previousGrade.replace("GRADE_", "G") : "Initial"}</span>
                → <span class="${this._gradeClass(gc.newGrade)}">${gc.newGrade.replace("GRADE_", "G")}</span>
              </td>
              <td>${gc.changedBy}</td>
              <td>${gc.reason ?? "—"}</td>
            </tr>
          `)}
        </tbody>
      </table>
    `;
  }
}
