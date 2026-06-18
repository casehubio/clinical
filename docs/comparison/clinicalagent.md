# casehub-clinical vs ClinicalAgent (arXiv 2404.14777)

ClinicalAgent (ACM BCB '24, open source) demonstrates naive LLM trial coordination:
a linear single-site pipeline with no compliance infrastructure.
This table maps each GCP/FDA requirement to the structural gap in ClinicalAgent
and the specific casehub-clinical class that closes it.

| GCP / FDA requirement | ClinicalAgent | casehub-clinical | Layer |
|---|---|---|---|
| Adverse event SLA — Grade 3/4 within 24h | No deadline tracking | `WorkItem.claimDeadline` — `AdverseEventService` | 2 |
| PI authorisation for protocol deviations | Agent autonomous | COMMAND commitment — `ProtocolDeviationService` | 3 |
| FDA tamper-evident audit | No audit trail | Merkle MMR — `AdverseEventLedgerEntry` | 4 |
| IRB gate for CRITICAL deviations | Not addressed | `deviation-review.yaml` humanTask; 72h `WorkItem` | 5 |
| GDPR consent withdrawal (Art.17) | Not applicable | `ConsentWithdrawalService` + `LedgerErasureService` | 8 |
| Multi-site independence | Single-site linear pipeline | Trial-level `CaseInstance`; per-site blackboard signals | 6 |
| Trust-weighted safety routing | No trust model | `ClinicalTrustRoutingPolicyProvider`; EigenTrust | 7 |
| IND expedited safety reporting | Not addressed | `RegulatorySubmissionCaseService`; 21 CFR 312.32 | 7 |
| Eligibility screening accountability | Agent decides; no record | `EligibilityScreeningLedgerEntry`; IRB gate if marginal | 9 |
| Protocol amendment LLM supervision | Not addressed | `ProtocolAmendmentAdvisor` SPI (clinical#86 / engine#101) | 9 |

Layer 9 = Showcase — new domain features exercising existing foundation layers (4+5)
without adding a new foundation module dependency.

## FDA independent verification

Without any server access, an FDA auditor can verify the complete decision chain
for every patient at every site:

```
GET /trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/ledger/verify
```

Returns a Merkle inclusion proof: `{ "valid": true, "merkleRoot": "..." }`.
The `merkleRoot` can be verified against a published checkpoint without
querying the server's database.

## Note on ARC42STORIES.MD

Layer 9 (Showcase) will be added to ARC42STORIES.MD §9.4 at epic close.
