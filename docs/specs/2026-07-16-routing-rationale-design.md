# Routing Rationale Component

**Issue:** #54
**Date:** 2026-07-16

## Problem

Trust-weighted routing decisions are opaque. When engine selects agent A over agent B for a capability, consumers (DevTown, AML, Clinical) have no visual explanation of why — score vs threshold, maturity phase, observation count, alternatives considered, borderline flagging.

## Data Source

Engine's `TrustCandidateClassifier` produces per-candidate classification (phase, trust score, workload score, final blended score) and `TrustRoutingPolicy` defines the selection parameters (threshold, borderlineMargin, blendFactor, minimumObservations, qualityFloors).

**No REST endpoint exists yet** — flagged as a gap in `devtown-ui-requirements.md`. The component supports both a `data` property (app passes data directly) and an `endpoint` (for when the API is built). Same dual-data pattern as similarity-panel and compliance-summary.

## Data Contract

```typescript
export interface RoutingRationaleData {
  capabilityTag: string;
  selected: CandidateScore;
  alternatives: CandidateScore[];
  policy: RoutingPolicySummary;
}

export interface CandidateScore {
  workerId: string;
  trustScore: number | null;
  phase: 'BOOTSTRAP' | 'QUALIFIED' | 'BORDERLINE' | 'EXCLUDED_PHASE2B' | 'EXCLUDED_PHASE3';
  observations: number;
  finalScore: number;
  exclusionReason?: string;
}

export interface RoutingPolicySummary {
  threshold: number;
  borderlineMargin: number;
  blendFactor: number;
  minimumObservations: number;
}
```

Maps directly to engine's `ClassifiedCandidate` record and `TrustRoutingPolicy` record. `trustScore` is `null` for BOOTSTRAP candidates (mirrors Java's `OptionalDouble.empty()`).

## Component API

```typescript
@customElement('routing-rationale')
export class RoutingRationale extends DataSourceMixin(LitElement) {
  // Data input (dual mode: property or endpoint)
  @property({ attribute: false }) data: RoutingRationaleData | null = null;

  // Customisation: typed config properties (per PP-20260713-8ea1af)
  @property({ type: String, attribute: 'score-label' }) scoreLabel = 'Trust Score';
  @property({ type: String, attribute: 'capability-label' }) capabilityLabel?: string;

  // Customisation: render callbacks (per PP-20260713-8ea1af)
  @property({ attribute: false }) renderCandidate?: (candidate: CandidateScore) => TemplateResult | undefined;

  // Events
  // candidate.selected — emitted when a row in the alternatives table is activated
}
```

## Visual Sections

### 1. Score Header

Selected candidate's score displayed as a horizontal bar against the threshold.

- Bar shows score (0–1 range) with the threshold as a vertical marker
- Borderline margin shown as a shaded band around the threshold
- Maturity phase badge (BOOTSTRAP / QUALIFIED / BORDERLINE / EXCLUDED)
- Observation count
- When `renderCandidate` callback is provided, its output replaces the default worker ID display

Phase badge colours:
- BOOTSTRAP: `--pages-neutral-*` (grey)
- QUALIFIED: `--pages-success-*` (green)
- BORDERLINE: `--pages-warning-*` (amber)
- EXCLUDED_PHASE2B, EXCLUDED_PHASE3: `--pages-danger-*` (red)

### 2. Alternatives Table

pages-table showing all candidates (selected highlighted at top).

| Column | Renderer |
|--------|----------|
| Worker | Plain text or `renderCandidate` callback |
| Score | Inline-styled bar (same pattern as similarity-panel fix) |
| Phase | Inline-styled badge |
| Observations | Plain number |
| Final Score | Inline-styled bar |
| Status | "Selected" badge or exclusion reason |

Column renderers use inline styles — not CSS classes — because renderer output lives in pages-table's shadow DOM (lesson from #67).

### 3. Policy Summary

Compact key-value row below the table:

```
Threshold: 0.70  |  Margin: ±0.10  |  Blend: 60% trust  |  Min observations: 10
```

## Pattern Alignment

- **DataSourceMixin**: same lifecycle as similarity-panel, compliance-summary, trust-score-panel
- **Protocol PP-20260713-8ea1af**: typed config + render callbacks, no slots
- **Inline styles in renderers**: column renderers use inline styles for cross-shadow-DOM correctness
- **pages-event**: emits via `emitPagesEvent` for candidate selection
- **CSS custom properties**: `--pages-*` tokens throughout

## Package Structure

```
components/routing-rationale/
  package.json
  tsconfig.json
  vitest.config.ts
  src/
    routing-rationale.ts
    routing-rationale.test.ts
    types.ts
```

## Consumers

- **DevTown**: reviewer profile detail tab — `renderCandidate` shows GitHub username + avatar
- **AML**: investigator assignment rationale
- **Clinical**: agent selection rationale

## Not In Scope

- REST endpoint in engine (separate issue — engine gap)
- Trend data / historical routing decisions
- Quality floor details (Phase 3 exclusions show "quality floor failed" but not which dimension)
