# Scenario Demo — Decisions

## D1: Action integration layer

**Choice:** @ScenarioAction methods exposed via GraphQL — scenario YAML uses `delivery: graphql` to call them
**Alternatives:**
- Direct service layer calls — faster but bypasses the scenario engine's GraphQL dispatcher
- REST endpoints — tests full HTTP stack but slower and requires HTTP client setup
**Rationale:** The scenario-client SDK auto-exposes @ScenarioAction as GraphQL mutations. This is the framework's intended integration point.
**Trade-offs:** Requires casehub-pages-scenario-client dependency and GraphQL wiring.
**Exploration:** quick
**Status:** captured

## D2: Action scope

**Choice:** ~10 actions mirroring DemoDataSeeder operations: createTrial, addSite, enrollPatient, screenPatient, reportAe, reportDeviation, approvePi, approveGate, activateTrial, verifyLedger
**Alternatives:**
- Granular CRUD (one per REST endpoint, ~16 actions) — more reusable but more maintenance
- Coarse composite (3-4 high-level actions) — fewer actions but less flexible for stepped demos
**Rationale:** Direct mirror of DemoDataSeeder makes migration verifiable — same operations, same sequence, same results.
**Trade-offs:** Some operations (like individual patient enrollment) won't be independently reusable outside this scenario.
**Depends on:** D1 (GraphQL layer)
**Exploration:** quick
**Status:** captured

## D3: Visual vs bulk split

**Choice:** Accountability moments visual, setup bulk. Bulk-seed: trial, sites, patients, screening. Visually drive: report AE, report deviation, approve PI, approve SUSAR gate, verify Merkle chain.
**Alternatives:**
- All visual — longer, more realistic, but slow for setup
- All backend — fast but loses "real user" credibility
**Rationale:** The governance story is what differentiates CaseHub. Setup is commodity; accountability is the demo.
**Trade-offs:** Setup steps can't be individually demonstrated without switching to stepped mode.
**Exploration:** quick
**Status:** captured

## D4: Scenario file location

**Choice:** Project repo at runtime/src/main/resources/scenarios/clinical-trial-demo.yaml — classpath-loadable, ships with the app
**Alternatives:**
- Workspace only — not shipped, development artifact only
**Rationale:** The scenario is a deliverable — part of the reference architecture. It should be version-controlled with the code.
**Trade-offs:** Changes to the scenario require a code commit.
**Exploration:** quick
**Status:** captured
