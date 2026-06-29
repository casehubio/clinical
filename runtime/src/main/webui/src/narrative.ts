export const STEP1_NARRATIVE = `This is ONCO-2024-001, a Phase III oncology trial across 3 sites. CaseHub coordinates AI agents for eligibility screening, safety monitoring, and protocol review — each governed by trust scores, oversight gates, and a tamper-evident audit trail.`;

export const STEP2_NARRATIVE = `CaseHub doesn't just run AI agents — it governs them. Each agent has a trust score built from its track record. High-stakes decisions are gated: no autonomous action on safety events. The platform selects agents by trust, gates their decisions, and records attestations that feed back into trust scores.`;

export const STEP3_NARRATIVE = `A CRITICAL protocol deviation is reported at Site B. Watch what happens: the platform sends a formal COMMAND to the named Principal Investigator — not a notification, an obligation. A Commitment is created with a deadline. If the PI doesn't respond, the platform escalates automatically. This is qhorus — formal accountability that no LLM pipeline can provide.`;

export const STEP4_NARRATIVE = `The PI receives a formal COMMAND with a 24-hour deadline. When they approve, the Commitment lifecycle closes. But this is a CRITICAL deviation — the policy requires IRB committee review. The case suspends in WAITING until the ethics committee decides within 72 hours. Every step — COMMAND, response, escalation — is recorded in the Merkle audit trail.`;

export const STEP5_NARRATIVE = `A Grade 4 hepatotoxicity event is reported at Site B. Watch what happens: the engine creates a 24-hour SLA work item, triggers SUSAR evaluation, and routes to a trust-weighted safety agent — all within seconds.`;

export const STEP6_NARRATIVE = `The SUSAR evaluator assessed this event: Grade 4 + unexpected + suspected = SUSAR criteria met. But the agent can't act alone — CaseHub's ActionRiskClassifier unconditionally gates all safety decisions. A qualified investigator must approve.`;

export const STEP7_NARRATIVE = `The investigator approves the SUSAR determination. CaseHub records the attestation — ENDORSED — which feeds into the agent's Bayesian trust score. Good decisions build trust; bad decisions erode it. The regulatory submission work item is created automatically.`;

export const STEP8_NARRATIVE = `Every decision is recorded in a tamper-evident Merkle audit trail. Each ledger entry is independently verifiable — no trust required in the platform itself. This is what FDA auditors and EU AI Act compliance officers need.`;
