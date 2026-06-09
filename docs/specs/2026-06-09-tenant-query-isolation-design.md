# Tenant Query Isolation — Design Spec
**Issue:** casehubio/clinical#71
**Date:** 2026-06-09

## Problem

`tenant_id` columns were added to all six core domain entities in V116 (`V116__add_tenant_id_domain_entities.sql`). Writes stamp the correct tenant via `CurrentPrincipal.tenancyId()`. Reads are unscoped — `Entity.findById(uuid)` returns any entity regardless of tenant. A REST caller in tenant A can retrieve, modify, or trigger actions on entities belonging to tenant B by guessing a UUID.

## Scope

**In scope:** REST-facing entity lookups and the one REST-called service (`TrialActivationService`) that receives a user-supplied entity ID with no prior resource-level validation.

**Out of scope — SponsorNotification:** `SponsorNotification` is a seventh entity with `tenant_id` (nullable, V115). The entity comment documents this: "Future multi-tenancy — nullable; findEligibleIds() must filter by tenantId when non-null." All `SponsorNotification.findById` calls are inside `SponsorNotificationStore`, which is called exclusively from `SponsorNotificationRetryJob` (a scheduler) and `SponsorNotificationDeliveryService` (triggered by a scheduler). There is no REST endpoint that looks up a `SponsorNotification` by ID. Tenant isolation for `SponsorNotification` is a separate concern tracked by its own `findEligibleIds` filtering, which is already specified in the entity comment.

**Out of scope — system-actor paths:** Internal service lookups in schedulers (`DeviationExpirer`), CDI observers (`SponsorNotificationListener`, `PiResponseListener`, `AeEscalationCaseService`, etc.), and system services (`TrialCaseLookup`) remain cross-tenant. These operate as system actors outside HTTP request boundaries and require that access by design.

## Architectural decision: entity-level helpers

**Mechanism chosen:** static `findByIdForTenant(UUID, CurrentPrincipal)` methods on each entity class, called at every REST-facing lookup.

**Why not Hibernate `@Filter`:** Hibernate 7's `SingleIdLoadPlan` pre-compiles the SQL AST at session-factory startup (`SingleIdLoadPlan` constructor, `jdbcSelect` field). Filter state is never consulted for primary key loads — the SQL is fixed before any session exists. `@Filter` only applies to HQL/JPQL/Criteria queries compiled at runtime. Every security-critical lookup in this codebase uses `findById()`. To use `@Filter` would require converting all `findById` to HQL `find("id = ?1", id).firstResult()` at every call site — the same change count as entity helpers — but trading explicit for implicit scoping, which is the wrong direction for GDPR-obligated code.

**Why not Hibernate `@TenantId` (discriminator):** `DeviationExpirer.findOverdueIds()` is a scheduler query that must see all tenants. `@TenantId` provides no per-query bypass. `@ObservesAsync` CDI observers run without request scope, breaking the resolver contract.

**Why entity helpers are right:**
- Call site is explicit: `findByIdForTenant` signals security intent at the point of use; `findById` signals system-actor access
- Works with Hibernate 7's actual SQL generation model
- System contexts use plain `findById` naturally — no bypass needed, no risk of accidental scoping
- Cross-tenant admin bypass is baked into each helper once, not scattered at call sites
- TDD isolation tests provide structural enforcement: a missed `findById` in a resource causes the cross-tenant isolation test to fail

## Entity helper contract

Added to all 6 core entities: `ClinicalTrial`, `TrialSite`, `PatientEnrollment`, `AdverseEvent`, `ProtocolDeviation`, `IrbApproval`.

```java
public static ClinicalTrial findByIdForTenant(UUID id, CurrentPrincipal principal) {
    if (principal.isCrossTenantAdmin()) return findById(id);
    return find("id = ?1 AND tenantId = ?2", id, principal.tenancyId()).firstResult();
}
```

Uses HQL `find()` — compiled at runtime; the tenant predicate is part of the SQL at execution time. Returns `null` on wrong tenant. All existing resource null-checks return 404 on null — no control-flow changes beyond the method swap.

`CurrentPrincipal` is already a declared dependency of `casehub-platform-api` in `runtime/`. No new module dependency.

`AdverseEvent` and `IrbApproval` receive the helper for consistency and forward-safety despite having no current REST GET endpoint.

## Query performance

`find("id = ?1 AND tenantId = ?2", id, tenantId)` generates:

```sql
SELECT * FROM clinical_trial WHERE id = ? AND tenant_id = ?
```

The PK predicate (`id = ?`) is evaluated first using the clustered index — it resolves to exactly one row. The `tenant_id` predicate is applied as a residual filter on that single row. Execution cost is identical to `findById`. No composite index on `(tenant_id, id)` is required for these entity-by-ID lookups. Future list endpoints (`WHERE tenant_id = ?`) will need a `tenant_id` index; that is a separate concern to be addressed when list queries are added.

## Cross-tenant admin write invariant

The `isCrossTenantAdmin()` bypass in `findByIdForTenant` is a read bypass: it allows the admin to load any entity regardless of tenant. For read-only endpoints (GET), this is complete. For mutation endpoints that create child entities, a further invariant must be preserved:

**Invariant:** `child.tenantId == parent.tenantId` for all hierarchically linked entities.

Without an explicit policy, a cross-tenant admin calling `POST /trials/{trialId}/sites` would load a trial from tenant A (via bypass), then stamp the new site with `principal.tenancyId()` (the admin's tenant). The result: `site.tenantId ≠ trial.tenantId`. That site is then invisible to tenant A's `TrialSite.findByIdForTenant(siteId, tenantAPrincipal)` — it exists but is unreachable from any tenant.

**Resolution:** All child entity creation derives `tenantId` from the parent entity, not from `principal.tenancyId()`. This is correct for all principals:
- Regular user: `parent.tenantId == principal.tenancyId()` always (the parent was loaded by `findByIdForTenant` which enforces this)
- Cross-tenant admin: child inherits the parent's actual tenant, not the admin's

`ClinicalTrial` is a root entity with no parent; `TrialResource.register` stamps from `principal.tenancyId()`, which is unchanged and correct.

**Implementation changes to create paths:**

| Resource / Service | Current stamping | Corrected stamping |
|---|---|---|
| `SiteResource.add` | `site.tenantId = principal.tenancyId()` | `site.tenantId = trial.tenantId` (trial already loaded in same `@Transactional`) |
| `PatientResource.enroll` | `enrollment.tenantId = principal.tenancyId()` | `enrollment.tenantId = site.tenantId` (site already loaded in same `@Transactional`) |
| `DeviationResource.reportDeviation` | `deviation.tenantId = principal.tenancyId()` | `deviation.tenantId = site.tenantId` (site loaded by `findByIdForTenant` in same `@Transactional`) |
| `AdverseEventService.reportAdverseEvent` | `ae.tenantId = principal.tenancyId()` | `ae.tenantId = enrollment.tenantId` (see refactoring below) |

**`AdverseEventService` refactoring:** The current code loads `PatientEnrollment` inside the private `resolveSiteId()` and discards the instance after extracting only `siteId`. `principal.tenancyId()` is then used for the tenant separately. This results in `tenantId` and `siteId` coming from different sources when they should come from the same entity.

Correct refactoring: load `PatientEnrollment` once at the top of `reportAdverseEvent`, derive both `siteId` and `tenantId` from that single instance, then load `TrialSite` once for `trialId`. Eliminate both private resolver methods (`resolveSiteId`, `resolveTrialId`).

```java
// Replace the two private-method calls:
PatientEnrollment enrollment = PatientEnrollment.findById(ae.enrollmentId);
UUID siteId = enrollment != null ? enrollment.siteId : null;
ae.tenantId = enrollment != null ? enrollment.tenantId : "default";

TrialSite site = siteId != null ? TrialSite.findById(siteId) : null;
UUID trialId = site != null ? site.trialId : null;
```

`CurrentPrincipal` was injected into `AdverseEventService` solely for the `ae.tenantId = principal.tenancyId()` line. After this change, that line is removed and the injection is dropped entirely. `TrialActivationService` gains a `CurrentPrincipal` injection; `AdverseEventService` loses one.

## Call site changes (read path)

### REST resources — 12 call sites

**`TrialResource`**
- `get(UUID id)` → `ClinicalTrial.findByIdForTenant(id, principal)`
- `updateSponsorConfig(UUID id)` → `ClinicalTrial.findByIdForTenant(id, principal)`

**`SiteResource`**
- `add(UUID trialId)` — load trial via `ClinicalTrial.findByIdForTenant(trialId, principal)` (store in local variable); stamp `site.tenantId = trial.tenantId`
- `get(UUID trialId, UUID siteId)` → `TrialSite.findByIdForTenant(siteId, principal)`; existing `!site.trialId.equals(trialId)` hierarchy check unchanged

**`PatientResource`**
- `enroll(trialId, siteId)` → `TrialSite.findByIdForTenant(siteId, principal)`; stamp `enrollment.tenantId = site.tenantId`
- `get(enrollmentId, siteId)` → `PatientEnrollment.findByIdForTenant` and `TrialSite.findByIdForTenant`
- `reportAdverseEvent(enrollmentId, siteId)` → same two lookups, both tenant-scoped

**`DeviationResource`**
- `reportDeviation(siteId)` → `TrialSite.findByIdForTenant(siteId, principal)` (store in local variable); stamp `deviation.tenantId = site.tenantId`
- `getDeviation(deviationId, siteId)` → `ProtocolDeviation.findByIdForTenant` and `TrialSite.findByIdForTenant`

### `TrialActivationService` — service-level enforcement

`TrialResource.activate(UUID id)` delegates to `TrialActivationService` with no prior tenant validation. Fix: inject `CurrentPrincipal` into `TrialActivationService`; `markActive(UUID trialId)` uses `ClinicalTrial.findByIdForTenant(trialId, principal)`. Wrong tenant returns null → existing `TrialNotFoundException` → 404.

`persistCaseId(UUID trialId, UUID caseId)` keeps plain `findById`. This is a phase-3 internal re-load of an entity already validated in phase 1 of the same request. No new security boundary.

## Deliberately unchanged — and the mechanisms that make them safe

### `ProtocolDeviationService.reportDeviation()` internal lookups — L1 cache

`ProtocolDeviationService.reportDeviation(ProtocolDeviation deviation)` calls `TrialSite.findById(deviation.siteId)` and `ClinicalTrial.findById(site.trialId)` for data enrichment (to get `investigatorId`, `protocolId`).

`DeviationResource.reportDeviation()` is `@Transactional`. `ProtocolDeviationService.reportDeviation()` is also `@Transactional(REQUIRED)`. They execute in the same JTA transaction and share the same Hibernate `Session`. The resource's `TrialSite.findByIdForTenant(siteId, principal)` — a HQL query — hydrates the `TrialSite` entity into the session's L1 (identity map) cache. The service's subsequent `TrialSite.findById(deviation.siteId)` translates to `session.find(TrialSite.class, siteId)` — an L1 cache lookup, not a new SQL query. No cross-tenant read occurs; the service reads the entity the resource already validated and cached.

The same mechanism applies to `ClinicalTrial.findById(site.trialId)` — the trial may also be in the L1 cache from earlier in the same transaction.

### `AdverseEventService` internal lookups — post-validation safety

`PatientResource.reportAdverseEvent()` has no `@Transactional`. The enrollment and site are loaded in autocommit sessions (short-lived). `AdverseEventService.reportAdverseEvent()` opens a new `@Transactional` transaction with a new `Session`. There is no shared L1 cache between resource and service.

Safety here is different: before the service is called, the resource has already called `PatientEnrollment.findByIdForTenant(enrollmentId, principal)` and `TrialSite.findByIdForTenant(siteId, principal)`. Both returned non-null — proving these entities belong to the current tenant. The IDs passed to the service are proven-tenant IDs. The service's plain `findById` of these proven-tenant entities cannot produce a cross-tenant exposure: the entity exists, is correct, and the service's lookup of it by primary key will find it.

### Other system-actor paths

| Code | Why unchanged |
|---|---|
| `DeviationExpirer.findOverdueIds()` and `expireOne()` | System scheduler; must see all tenants |
| `SponsorNotificationListener`, `PiResponseListener`, `IrbDeviationCaseService`, `AeEscalationCaseService` | `@ObservesAsync` CDI observers; system actor context, no request scope |
| `TrialCaseLookup` | Engine integration utility; system service |

## Testing

**Pattern — isolation test (wrong tenant → 404):**

```java
@Inject FixedCurrentPrincipal principal;

@AfterEach
void resetPrincipal() { principal.reset(); }

@Test
void get_returns_404_for_wrong_tenant() {
    UUID id = createTrial();                    // created under default test tenant
    principal.setTenancyId("other-tenant");
    given().when().get("/trials/{id}", id).then().statusCode(404);
}
```

`FixedCurrentPrincipal` is `@ApplicationScoped` — switching its `tenancyId` affects all CDI injection points for the remainder of that HTTP request. The `@AfterEach` reset is required in **every test class** that injects `FixedCurrentPrincipal` to prevent principal state from bleeding across tests within the class.

**Pattern — bypass test (cross-tenant admin → 200):**

```java
@Test
void get_succeeds_for_cross_tenant_admin() {
    UUID id = createTrial();                    // created under default test tenant
    principal.setTenancyId("other-tenant");
    principal.setCrossTenantAdmin(true);
    given().when().get("/trials/{id}", id).then().statusCode(200);
}
```

**Pattern — write invariant test (child inherits parent's tenant, not admin's):**

The write-path invariant is only observable when `principal.tenancyId()` differs from the parent entity's `tenantId` — i.e., in the cross-tenant admin scenario. The two-assertion structure enforces this:

```java
@Test
void site_inherits_trial_tenantId_not_principal_tenantId() {
    UUID trialId = createTrial();              // trial.tenantId = default test tenant
    principal.setTenancyId("admin-tenant");
    principal.setCrossTenantAdmin(true);
    UUID siteId = addSite(trialId);           // site.tenantId must = trial.tenantId, not "admin-tenant"

    principal.reset();                         // back to default test tenant
    given().get("/trials/{t}/sites/{s}", trialId, siteId).then().statusCode(200);

    principal.setTenancyId("admin-tenant");    // admin's own tenant, no bypass this time
    given().get("/trials/{t}/sites/{s}", trialId, siteId).then().statusCode(404);
    // 404 proves site.tenantId ≠ "admin-tenant"; if it were, this GET would return 200
}
```

The second assertion is the structural enforcement: if `site.tenantId` were stamped with the admin's tenant, the second GET (which is NOT a bypass) would return 200 instead of 404.

**New tests per class:**

| Test class | Isolation (wrong tenant → 404) | Bypass (admin → 200) | Write invariant |
|---|---|---|---|
| `TrialResourceTest` | GET wrong tenant; PATCH wrong tenant | GET as admin | — (root entity, no parent to inherit from) |
| `TrialActivationTest` | POST activate wrong-tenant trial → HTTP 404 | POST activate cross-tenant trial as admin → HTTP 204 | — |
| `SiteResourceTest` | GET site wrong tenant; add site to wrong-tenant trial | GET site as admin | `site_inherits_trial_tenantId_not_principal_tenantId` |
| `PatientResourceTest` | GET enrollment wrong tenant; report AE on wrong-tenant enrollment | GET enrollment as admin | `enrollment_inherits_site_tenantId_not_principal_tenantId` |
| `DeviationResourceTest` | GET deviation wrong tenant | GET deviation as admin | `deviation_inherits_site_tenantId_not_principal_tenantId` |
| `AdverseEventServiceTest` | — (no REST GET) | — | `ae_tenantId_is_derived_from_enrollment_not_principal` (new test) |

**Setup note — all five test classes:** None of the five resource/service test classes in the table currently inject `FixedCurrentPrincipal`. Every class that adds isolation, bypass, or write-invariant tests must add:

```java
@Inject FixedCurrentPrincipal principal;

@AfterEach
void resetPrincipal() { principal.reset(); }
```

This applies to: `TrialResourceTest`, `TrialActivationTest`, `SiteResourceTest`, `PatientResourceTest`, `DeviationResourceTest`. `AdverseEventServiceTest` does not need it — its write-invariant test relies on entity field defaults, not principal switching.

**`AdverseEventServiceTest` write invariant:** `newAe()` creates entities directly (not via REST), so `enrollment.tenantId` defaults to `"default"` (entity field initializer). `FixedCurrentPrincipal.tenancyId()` returns `"278776f9-..."`. These values differ without any cross-tenant admin setup, so the assertion catches the derivation source directly:

```java
@Test
@Transactional
void ae_tenantId_is_derived_from_enrollment_not_principal() {
    AdverseEvent ae = newAe(CtcaeGrade.GRADE_1);
    PatientEnrollment enrollment = PatientEnrollment.findById(ae.enrollmentId);
    service.reportAdverseEvent(ae);
    assertThat(ae.tenantId).isEqualTo(enrollment.tenantId);  // "default", not "278776f9-..."
}
```

The test is `@Transactional`, so the enrollment loaded in the test and the enrollment loaded by the service (via `PatientEnrollment.findById`) share the same Hibernate session and the same L1 cache — no extra DB round-trip.

**`TrialActivationTest` bypass test rationale:** This test validates that `CurrentPrincipal` injection in `TrialActivationService` is wired correctly at the service layer. This is a new injection point not previously in that service. `TrialResourceTest.get_succeeds_for_cross_tenant_admin` verifies the entity helper at the resource layer; a misconfigured CDI injection at the service scope would go undetected without this additional test. `activate()` is synchronous (calls `.join()` internally; 204 returns only after all three phases complete), so no Awaitility is needed for the HTTP status assertion.

## Flyway migrations

None. `tenant_id` columns exist on all 6 core domain entities from V116. `SponsorNotification.tenant_id` exists from V115. This change is query-layer and write-stamping-logic only.
