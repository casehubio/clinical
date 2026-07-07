import { LitElement, html, css } from "lit";
import { property } from "lit/decorators.js";

export interface TierDefinition {
  readonly threshold: number;
  readonly label: string;
  readonly consequence: string;
  readonly regulation?: string;
}

export class ClinicalSlaBreachPolicyIndicator extends LitElement {
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
    .tier-list {
      display: flex;
      flex-direction: column;
      gap: var(--pages-space-3, 0.75rem);
    }
    .tier {
      display: flex;
      align-items: flex-start;
      gap: var(--pages-space-3, 0.75rem);
      padding: var(--pages-space-3, 0.75rem);
      border-radius: var(--pages-radius-2, 4px);
      border: 2px solid var(--pages-neutral-4, #eee);
      background: white;
      transition: all 0.2s ease;
    }
    .tier--active {
      border-color: var(--pages-orange-9, #e67e22);
      background: var(--pages-orange-1, #fff9f5);
      box-shadow: 0 2px 8px rgba(230, 126, 34, 0.15);
    }
    .tier-node {
      flex-shrink: 0;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 600;
      font-size: 13px;
      background: var(--pages-neutral-5, #ddd);
      color: var(--pages-neutral-11, #7f8c8d);
    }
    .tier--active .tier-node {
      background: var(--pages-orange-9, #e67e22);
      color: white;
      animation: pulse 2s infinite;
    }
    .tier-content {
      flex: 1;
    }
    .tier-header {
      display: flex;
      align-items: center;
      gap: var(--pages-space-2, 0.5rem);
      margin-bottom: var(--pages-space-2, 0.5rem);
    }
    .tier-label {
      font-weight: 600;
      font-size: 14px;
      color: var(--pages-neutral-12, #2c3e50);
    }
    .tier-threshold {
      font-size: 12px;
      color: var(--pages-neutral-9, #95a5a6);
    }
    .tier--active .tier-label {
      color: var(--pages-orange-11, #8a4000);
    }
    .tier-consequence {
      font-size: 13px;
      color: var(--pages-neutral-11, #7f8c8d);
      margin-bottom: var(--pages-space-1, 0.25rem);
    }
    .tier-regulation {
      font-size: 12px;
      color: var(--pages-neutral-9, #95a5a6);
      font-style: italic;
    }
    @keyframes pulse {
      0%,
      100% {
        opacity: 1;
      }
      50% {
        opacity: 0.7;
      }
    }
  `;

  @property({ attribute: false }) tiers: TierDefinition[] = [];
  @property({ type: Number }) timeRemaining = 0;

  private _isActiveTier(tier: TierDefinition): boolean {
    if (!this.tiers.length) return false;

    // Find the active tier based on timeRemaining
    // Active tier is the one whose threshold is closest to but not less than the normalized time
    const normalizedTime = this.timeRemaining / 100; // Assuming timeRemaining is a percentage

    // Sort tiers by threshold
    const sortedTiers = [...this.tiers].sort((a, b) => a.threshold - b.threshold);

    // Find first tier where threshold >= normalizedTime
    for (let i = 0; i < sortedTiers.length; i++) {
      if (sortedTiers[i].threshold >= normalizedTime) {
        return sortedTiers[i] === tier;
      }
    }

    // If timeRemaining exceeds all thresholds, the last tier is active
    return tier === sortedTiers[sortedTiers.length - 1];
  }

  render() {
    if (!this.tiers.length) {
      return html`<div class="empty">No breach policy tiers defined</div>`;
    }

    return html`
      <div class="tier-list">
        ${this.tiers.map(
          (tier, index) => html`
            <div class="tier ${this._isActiveTier(tier) ? "tier--active" : ""}">
              <div class="tier-node">${index + 1}</div>
              <div class="tier-content">
                <div class="tier-header">
                  <span class="tier-label">${tier.label}</span>
                  <span class="tier-threshold">at ${(tier.threshold * 100).toFixed(0)}%</span>
                </div>
                <div class="tier-consequence">${tier.consequence}</div>
                ${tier.regulation ? html`<div class="tier-regulation">${tier.regulation}</div>` : ""}
              </div>
            </div>
          `
        )}
      </div>
    `;
  }
}
