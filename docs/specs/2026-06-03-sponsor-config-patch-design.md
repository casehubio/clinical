# Design — PATCH /trials/{id}/sponsor-config

**Issue:** casehubio/clinical#22
**Date:** 2026-06-03

## Summary

Add `PATCH /trials/{id}/sponsor-config` to allow updating sponsor notification config on an existing trial. Fields already exist on `ClinicalTrial` entity from clinical#13 — no migration needed.

## Endpoint

```
PATCH /trials/{id}/sponsor-config
Content-Type: application/json

{
  "connectorId": "slack",
  "destination": "https://hooks.slack.com/T000/B000/xxx"
}
```

**Semantics:** Full replace. Both fields are updated atomically. Either or both may be null to clear the config.

**connectorId validation:** Accept any string — delivery failure is the notifier's responsibility, not validated at the REST layer.

## Response Codes

| Code | Condition |
|------|-----------|
| 204 No Content | Success (idempotent) |
| 404 Not Found | Trial does not exist |
| 400 Bad Request | Size constraint violated |

## Implementation

`SponsorConfigRequest` record added as nested type in `TrialResource` (consistent with `RegisterTrialRequest`). Logic inline in resource — two-column update with no business rules. Size constraints: `@Size(max = 64)` for `connectorId`, `@Size(max = 2048)` for `destination`.

## Tests (QuarkusTest)

1. PATCH existing trial → 204, GET confirms new values persisted
2. PATCH with both null → 204, GET shows null fields (config cleared)
3. PATCH unknown trial → 404
4. PATCH connectorId > 64 chars → 400
