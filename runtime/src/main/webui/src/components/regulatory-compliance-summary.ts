import { LitElement, html, css } from "lit";
import { property } from "lit/decorators.js";

export interface RequirementDefinition {
  readonly regulation: string;
  readonly requirement: string;
  readonly mechanism: string;
  readonly status: "MET" | "PARTIAL" | "GAP" | "BREACHED";
  readonly evidenceUrl?: string;
}

export class ClinicalRegulatoryComplianceSummary extends LitElement {
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
    .status-badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: var(--pages-radius-2, 4px);
      font-weight: 600;
      font-size: 12px;
    }
    .status-badge--met {
      background: #27ae60;
      color: white;
    }
    .status-badge--partial {
      background: #f39c12;
      color: white;
    }
    .status-badge--gap {
      background: #e67e22;
      color: white;
    }
    .status-badge--breached {
      background: #e74c3c;
      color: white;
    }
    .evidence-link {
      color: var(--pages-accent-9, #3498db);
      text-decoration: none;
      font-size: 12px;
    }
    .evidence-link:hover {
      text-decoration: underline;
    }
  `;

  @property({ attribute: false }) requirements: RequirementDefinition[] = [];
  @property() endpoint = "";

  private _getStatusClass(status: string): string {
    return `status-badge--${status.toLowerCase()}`;
  }

  render() {
    if (!this.requirements.length) {
      return html`<div class="empty">No regulatory requirements defined</div>`;
    }

    return html`
      <div class="table-container">
        <table>
          <thead>
            <tr>
              <th>Regulation</th>
              <th>Requirement</th>
              <th>Mechanism</th>
              <th>Status</th>
              <th>Evidence</th>
            </tr>
          </thead>
          <tbody>
            ${this.requirements.map(
              req => html`
                <tr>
                  <td>${req.regulation}</td>
                  <td>${req.requirement}</td>
                  <td>${req.mechanism}</td>
                  <td>
                    <span class="status-badge ${this._getStatusClass(req.status)}">${req.status}</span>
                  </td>
                  <td>
                    ${req.evidenceUrl
                      ? html`<a href="${req.evidenceUrl}" class="evidence-link" target="_blank">View</a>`
                      : html`<span style="color: var(--pages-neutral-9, #95a5a6);">—</span>`}
                  </td>
                </tr>
              `
            )}
          </tbody>
        </table>
      </div>
    `;
  }
}
