import { LitElement, html, css } from "lit";
import { property } from "lit/decorators.js";

export interface GateDecision {
  readonly decision: string;
  readonly investigator: string;
  readonly attestation: string;
  readonly trustScoreBefore: number;
  readonly trustScoreAfter: number;
  readonly dimension: string;
}

export class ClinicalTrustFeedbackDisplay extends LitElement {
  static styles = css`
    :host {
      display: block;
      font-family: var(--pages-font-family, sans-serif);
    }
    .card {
      border: 1px solid var(--pages-neutral-4, #eee);
      border-radius: var(--pages-radius-3, 8px);
      padding: var(--pages-space-4, 1rem);
      background: white;
    }
    .row {
      display: flex;
      justify-content: space-between;
      padding: var(--pages-space-2, 0.5rem) 0;
      border-bottom: 1px solid var(--pages-neutral-3, #ecf0f1);
    }
    .row:last-child {
      border-bottom: none;
    }
    .label {
      font-weight: 600;
      color: var(--pages-neutral-11, #7f8c8d);
      font-size: 13px;
    }
    .value {
      color: var(--pages-neutral-12, #2c3e50);
      font-size: 13px;
    }
    .decision-badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: var(--pages-radius-2, 4px);
      font-weight: 600;
      font-size: 13px;
    }
    .decision-badge--approved {
      background: var(--pages-green-3, #d4edda);
      color: var(--pages-green-11, #155724);
    }
    .decision-badge--rejected {
      background: var(--pages-red-3, #f8d7da);
      color: var(--pages-red-11, #721c24);
    }
    .attestation-badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: var(--pages-radius-2, 4px);
      font-size: 13px;
    }
    .attestation-badge--endorsed {
      background: var(--pages-accent-3, #d1ecf1);
      color: var(--pages-accent-11, #0c5460);
    }
    .attestation-badge--overruled {
      background: var(--pages-orange-3, #ffe5d0);
      color: var(--pages-orange-11, #8a4000);
    }
    .trust-delta {
      display: flex;
      align-items: center;
      gap: var(--pages-space-2, 0.5rem);
    }
    .arrow {
      font-weight: bold;
      font-size: 16px;
    }
    .arrow--up {
      color: var(--pages-green-9, #27ae60);
    }
    .arrow--down {
      color: var(--pages-red-9, #e74c3c);
    }
    .arrow--neutral {
      color: var(--pages-neutral-9, #95a5a6);
    }
    .compact {
      display: flex;
      align-items: center;
      gap: var(--pages-space-3, 0.75rem);
      font-size: 13px;
      padding: var(--pages-space-2, 0.5rem);
    }
  `;

  @property({ attribute: false }) gateDecision: GateDecision | null = null;
  @property({ type: Boolean }) compact = false;

  private _getDecisionClass(decision: string): string {
    return decision === "APPROVED" ? "decision-badge--approved" : "decision-badge--rejected";
  }

  private _getAttestationClass(attestation: string): string {
    return attestation === "ENDORSED" ? "attestation-badge--endorsed" : "attestation-badge--overruled";
  }

  private _getTrustArrow(before: number, after: number): { symbol: string; className: string } {
    if (after > before) return { symbol: "↑", className: "arrow--up" };
    if (after < before) return { symbol: "↓", className: "arrow--down" };
    return { symbol: "→", className: "arrow--neutral" };
  }

  render() {
    if (!this.gateDecision) {
      return html`<div style="color: var(--pages-neutral-9, #95a5a6); padding: 1rem;">No gate decision data</div>`;
    }

    const { decision, investigator, attestation, trustScoreBefore, trustScoreAfter, dimension } = this.gateDecision;
    const arrow = this._getTrustArrow(trustScoreBefore, trustScoreAfter);

    if (this.compact) {
      return html`
        <div class="compact">
          <span class="decision-badge ${this._getDecisionClass(decision)}">${decision}</span>
          <span>${investigator}</span>
          <span class="attestation-badge ${this._getAttestationClass(attestation)}">${attestation}</span>
          <span class="trust-delta">
            ${trustScoreBefore.toFixed(2)}
            <span class="arrow ${arrow.className}">${arrow.symbol}</span>
            ${trustScoreAfter.toFixed(2)}
          </span>
          <span style="color: var(--pages-neutral-9, #95a5a6);">${dimension}</span>
        </div>
      `;
    }

    return html`
      <div class="card">
        <div class="row">
          <span class="label">Gate Decision</span>
          <span class="value">
            <span class="decision-badge ${this._getDecisionClass(decision)}">${decision}</span>
          </span>
        </div>
        <div class="row">
          <span class="label">Approver</span>
          <span class="value">${investigator}</span>
        </div>
        <div class="row">
          <span class="label">Attestation Verdict</span>
          <span class="value">
            <span class="attestation-badge ${this._getAttestationClass(attestation)}">${attestation}</span>
          </span>
        </div>
        <div class="row">
          <span class="label">Trust Score Delta</span>
          <span class="value">
            <span class="trust-delta">
              ${trustScoreBefore.toFixed(2)}
              <span class="arrow ${arrow.className}">${arrow.symbol}</span>
              ${trustScoreAfter.toFixed(2)}
            </span>
          </span>
        </div>
        <div class="row">
          <span class="label">Dimension</span>
          <span class="value">${dimension}</span>
        </div>
      </div>
    `;
  }
}
