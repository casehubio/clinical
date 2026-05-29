package io.casehub.clinical.service;

// Happy-path coverage moved to AeEscalationLifecycleTest (@QuarkusTest) after three-phase
// refactor introduced Panache calls in Phase 1 — incompatible with @InjectMocks.
//
// Grade4/5 signaling coverage gap tracked in casehubio/clinical#42.
// Guard/filter tests not needed — AeEscalationCaseService has no early-return guards.
class AeEscalationCaseServiceTest {
}
