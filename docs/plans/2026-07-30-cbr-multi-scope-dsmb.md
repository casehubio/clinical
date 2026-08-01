# CBR Multi-Scope Memory for DSMB Pattern Detection — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #120 — feat: CBR Phase 7 — multi-scope memory for DSMB pattern detection
**Issue group:** #120

**Goal:** Wire neocortex's scope hierarchy, scope decay, trust weighting, temporal decay, supersession, and retention into clinical's CBR layer. Build a cross-scope aggregation job for trial-level safety signals. Add GDPR-compliant patient-scope CBR erasure.

**Architecture:** All scope/decay/trust infrastructure exists in neocortex. Clinical currently passes `Path.root()` everywhere. This work replaces `Path.root()` with proper hierarchical scope paths (`trial → site → patient`), enables neocortex decorators (trust weighting, temporal decay), adds a new aggregation domain for DSMB signals, and wires GDPR erasure to scope-based CBR deletion.

**Tech Stack:** Java 21, Quarkus 3.32.2, casehub-neocortex 0.2-SNAPSHOT (CBR), casehub-ledger 0.2-SNAPSHOT (trust scores), casehub-platform-api (Path)

## Global Constraints

- `ClinicalScope` enum goes in `api/` module (follows `CtcaeGrade`, `DeviationSeverity` pattern)
- `ClinicalScopeResolver` and all new runtime classes go in `runtime/` module
- New Flyway migration uses V128 in `db/migration/default/`
- `CbrCaseMemoryStore.store()` 7th param is `Path scope` — never pass `Path.root()` after this work
- `CbrQuery.withScope()` and `.withScopeDecay()` are the builder methods for scoped queries
- `storeIdempotent()` uses erase-before-store — scope must match between erase and store calls
- Trust weighting enabled via `casehub.cbr.trust-weighting.enabled=true`
- `SiteEnrollmentTrajectoryJob` and `SponsorNotificationRetryJob` are excluded from test classpath via `quarkus.arc.exclude-types`
- IntelliJ MCP project path: `/Users/mdproctor/claude/casehub/clinical`

---

### Task 1: ClinicalScope enum and ClinicalScopeResolver

**Files:**
- Create: `api/src/main/java/io/casehub/clinical/api/model/ClinicalScope.java`
- Create: `runtime/src/main/java/io/casehub/clinical/cbr/ClinicalScopeResolver.java`
- Test: `api/src/test/java/io/casehub/clinical/api/model/ClinicalScopeTest.java`
- Test: `runtime/src/test/java/io/casehub/clinical/cbr/ClinicalScopeResolverTest.java`

**Interfaces:**
- Produces: `ClinicalScope.TRIAL` (depth 1), `ClinicalScope.SITE` (depth 2), `ClinicalScope.PATIENT` (depth 3)
- Produces: `ClinicalScopeResolver.forAdverseEvent(AdverseEvent) → Optional<Path>`, `forDeviation(ProtocolDeviation) → Optional<Path>`, `forAmendment(ProtocolAmendment) → Optional<Path>`, `forSiteEnrollment(TrialSite) → Optional<Path>`, `forTrial(ClinicalTrial) → Optional<Path>`

- [ ] **Step 1: Write ClinicalScope enum test**

```java
package io.casehub.clinical.api.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClinicalScopeTest {

    @Test
    void trialHasDepthOne() {
        assertEquals(1, ClinicalScope.TRIAL.depth());
    }

    @Test
    void siteHasDepthTwo() {
        assertEquals(2, ClinicalScope.SITE.depth());
    }

    @Test
    void patientHasDepthThree() {
        assertEquals(3, ClinicalScope.PATIENT.depth());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl api -Dtest=ClinicalScopeTest --batch-mode`
Expected: FAIL — `ClinicalScope` not found

- [ ] **Step 3: Implement ClinicalScope enum**

```java
package io.casehub.clinical.api.model;

public enum ClinicalScope {
    TRIAL(1),
    SITE(2),
    PATIENT(3);

    private final int depth;

    ClinicalScope(int depth) {
        this.depth = depth;
    }

    public int depth() {
        return depth;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl api -Dtest=ClinicalScopeTest --batch-mode`
Expected: PASS

- [ ] **Step 5: Write ClinicalScopeResolver test**

Unit test with a mock `EntityResolver` (same pattern as `AeEscalationPlanRetriever`). Test all five methods and the `Optional.empty()` null-guard path.

```java
package io.casehub.clinical.cbr;

import io.casehub.clinical.entity.*;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClinicalScopeResolverTest {

    private ClinicalScopeResolver resolver;
    private UUID trialId, siteId, enrollmentId, patientId;

    @BeforeEach
    void setup() {
        trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();
        patientId = UUID.randomUUID();

        resolver = new ClinicalScopeResolver();
        resolver.setEntityResolver(new ClinicalScopeResolver.EntityResolver() {
            @Override public PatientEnrollment findEnrollment(UUID id) {
                if (!id.equals(enrollmentId)) return null;
                PatientEnrollment e = new PatientEnrollment();
                e.id = enrollmentId;
                e.siteId = siteId;
                e.patientId = patientId.toString();
                return e;
            }
            @Override public TrialSite findSite(UUID id) {
                if (!id.equals(siteId)) return null;
                TrialSite s = new TrialSite();
                s.id = siteId;
                s.trialId = trialId;
                return s;
            }
            @Override public ClinicalTrial findTrial(UUID id) {
                if (!id.equals(trialId)) return null;
                ClinicalTrial t = new ClinicalTrial();
                t.id = trialId;
                return t;
            }
        });
    }

    @Test
    void forAdverseEvent_returnsPatientScope() {
        AdverseEvent ae = new AdverseEvent();
        ae.enrollmentId = enrollmentId;
        Optional<Path> scope = resolver.forAdverseEvent(ae);
        assertTrue(scope.isPresent());
        assertEquals(Path.of(trialId.toString(), siteId.toString(), patientId.toString()), scope.get());
        assertEquals(3, scope.get().depth());
    }

    @Test
    void forAdverseEvent_returnsEmptyWhenEnrollmentNotFound() {
        AdverseEvent ae = new AdverseEvent();
        ae.enrollmentId = UUID.randomUUID();
        assertTrue(resolver.forAdverseEvent(ae).isEmpty());
    }

    @Test
    void forDeviation_returnsSiteScope() {
        ProtocolDeviation dev = new ProtocolDeviation();
        dev.siteId = siteId;
        Optional<Path> scope = resolver.forDeviation(dev);
        assertTrue(scope.isPresent());
        assertEquals(Path.of(trialId.toString(), siteId.toString()), scope.get());
        assertEquals(2, scope.get().depth());
    }

    @Test
    void forAmendment_returnsTrialScope() {
        ProtocolAmendment amend = new ProtocolAmendment();
        amend.trialId = trialId;
        Optional<Path> scope = resolver.forAmendment(amend);
        assertTrue(scope.isPresent());
        assertEquals(Path.of(trialId.toString()), scope.get());
        assertEquals(1, scope.get().depth());
    }

    @Test
    void forSiteEnrollment_returnsSiteScope() {
        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        Optional<Path> scope = resolver.forSiteEnrollment(site);
        assertTrue(scope.isPresent());
        assertEquals(Path.of(trialId.toString(), siteId.toString()), scope.get());
    }

    @Test
    void forTrial_returnsTrialScope() {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        Optional<Path> scope = resolver.forTrial(trial);
        assertTrue(scope.isPresent());
        assertEquals(Path.of(trialId.toString()), scope.get());
    }
}
```

- [ ] **Step 6: Implement ClinicalScopeResolver**

```java
package io.casehub.clinical.cbr;

import io.casehub.clinical.entity.*;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ClinicalScopeResolver {

    private EntityResolver entityResolver = new PanacheEntityResolver();

    void setEntityResolver(EntityResolver resolver) {
        this.entityResolver = resolver;
    }

    public Optional<Path> forAdverseEvent(AdverseEvent ae) {
        if (ae.enrollmentId == null) return Optional.empty();
        PatientEnrollment enrollment = entityResolver.findEnrollment(ae.enrollmentId);
        if (enrollment == null) return Optional.empty();
        TrialSite site = entityResolver.findSite(enrollment.siteId);
        if (site == null) return Optional.empty();
        return Optional.of(Path.of(site.trialId.toString(), enrollment.siteId.toString(), enrollment.patientId));
    }

    public Optional<Path> forDeviation(ProtocolDeviation dev) {
        if (dev.siteId == null) return Optional.empty();
        TrialSite site = entityResolver.findSite(dev.siteId);
        if (site == null) return Optional.empty();
        return Optional.of(Path.of(site.trialId.toString(), dev.siteId.toString()));
    }

    public Optional<Path> forAmendment(ProtocolAmendment amend) {
        if (amend.trialId == null) return Optional.empty();
        return Optional.of(Path.of(amend.trialId.toString()));
    }

    public Optional<Path> forSiteEnrollment(TrialSite site) {
        if (site.trialId == null) return Optional.empty();
        return Optional.of(Path.of(site.trialId.toString(), site.id.toString()));
    }

    public Optional<Path> forTrial(ClinicalTrial trial) {
        if (trial.id == null) return Optional.empty();
        return Optional.of(Path.of(trial.id.toString()));
    }

    interface EntityResolver {
        PatientEnrollment findEnrollment(UUID id);
        TrialSite findSite(UUID id);
        ClinicalTrial findTrial(UUID id);
    }

    private static class PanacheEntityResolver implements EntityResolver {
        @Override public PatientEnrollment findEnrollment(UUID id) { return PatientEnrollment.findById(id); }
        @Override public TrialSite findSite(UUID id) { return TrialSite.findById(id); }
        @Override public ClinicalTrial findTrial(UUID id) { return ClinicalTrial.findById(id); }
    }
}
```

- [ ] **Step 7: Run tests and verify pass**

Run: `mvn install -pl api --batch-mode && mvn test -pl runtime -Dtest=ClinicalScopeResolverTest --batch-mode`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add api/src/main/java/io/casehub/clinical/api/model/ClinicalScope.java \
        api/src/test/java/io/casehub/clinical/api/model/ClinicalScopeTest.java \
        runtime/src/main/java/io/casehub/clinical/cbr/ClinicalScopeResolver.java \
        runtime/src/test/java/io/casehub/clinical/cbr/ClinicalScopeResolverTest.java
git commit -m "feat(#120): add ClinicalScope enum and ClinicalScopeResolver

Refs #120"
```

---

### Task 2: Update ClinicalCbrService and all writers to use scoped storage

**Files:**
- Modify: `runtime/src/main/java/io/casehub/clinical/cbr/ClinicalCbrService.java`
- Modify: `runtime/src/main/java/io/casehub/clinical/cbr/ClinicalCaseOutcomeObserver.java`
- Modify: `runtime/src/main/java/io/casehub/clinical/cbr/DeviationResolutionCbrWriter.java`
- Modify: `runtime/src/main/java/io/casehub/clinical/cbr/AmendmentResolutionCbrWriter.java`
- Modify: `runtime/src/main/java/io/casehub/clinical/cbr/SiteEnrollmentTrajectoryJob.java`
- Modify: `runtime/src/main/java/io/casehub/clinical/cbr/TrialCompletionSiteTrajectoryWriter.java`
- Modify: `runtime/src/test/java/io/casehub/clinical/cbr/ClinicalCbrServiceTest.java`
- Modify: `runtime/src/test/java/io/casehub/clinical/cbr/ClinicalCbrServiceAuditTest.java`
- Modify: `runtime/src/test/java/io/casehub/clinical/cbr/DeviationResolutionCbrWriterTest.java`
- Modify: `runtime/src/test/java/io/casehub/clinical/cbr/AmendmentResolutionCbrWriterTest.java`
- Modify: `runtime/src/test/java/io/casehub/clinical/cbr/SiteEnrollmentTrajectoryJobTest.java`
- Modify: any other test that calls `storeIdempotent`

**Interfaces:**
- Consumes: `ClinicalScopeResolver` from Task 1
- Produces: `ClinicalCbrService.storeIdempotent(CbrCase, String, String, MemoryDomain, String, String, Path)` — new 7-arg signature

- [ ] **Step 1: Update ClinicalCbrService.storeIdempotent signature**

Add `Path scope` as the 7th parameter. Replace `Path.root()` with `scope`.

```java
public String storeIdempotent(final CbrCase cbrCase, final String caseType,
                              final String entityId, final MemoryDomain domain,
                              final String tenantId, final String caseId,
                              final Path scope) {
    store.eraseEntity(entityId, tenantId);
    return store.store(cbrCase, caseType, entityId, domain, tenantId, caseId, scope);
}
```

- [ ] **Step 2: Find all callers of storeIdempotent and update**

Use `ide_find_references` on `storeIdempotent` to find all call sites. Each caller needs:
1. Inject `ClinicalScopeResolver`
2. Resolve scope from the domain entity
3. Pass scope as 7th arg (skip storage if `Optional.empty()`)

**ClinicalCaseOutcomeObserver.handleAeCase:** Resolve scope via `scopeResolver.forAdverseEvent(ae)`. Guard: if empty, log warning and return.

**DeviationResolutionCbrWriter.buildAndStore:** Resolve via `scopeResolver.forDeviation(deviation)`. Guard: if empty, log warning and return.

**AmendmentResolutionCbrWriter.onAmendmentResolved:** Resolve via `scopeResolver.forAmendment(amendment)`. Guard: if empty, log warning and return.

**SiteEnrollmentTrajectoryJob.snapshotSite:** Resolve via `scopeResolver.forSiteEnrollment(site)`. The site is already loaded — pass directly.

**TrialCompletionSiteTrajectoryWriter.storeForSite:** Same as above.

- [ ] **Step 3: Update existing tests**

All tests that call `storeIdempotent` with 6 args need the 7th `Path` arg added. Use `Path.of("trial-1", "site-1", "patient-1")` or appropriate test values. For tests that mock `ClinicalCbrService`, update mock stubs to match the new signature.

- [ ] **Step 4: Run full test suite**

Run: `mvn install -pl api --batch-mode && mvn test -pl runtime --batch-mode`
Expected: PASS — all existing tests updated, no `Path.root()` calls remain in writers

- [ ] **Step 5: Verify no remaining Path.root() in CBR writers**

Use `ide_search_text` for `Path.root()` in `runtime/src/main/java/io/casehub/clinical/cbr/`. Only `ClinicalCbrService` should be free of it (it was the only place). Confirm all 5 writers pass scoped paths.

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(#120): wire scoped CBR storage — replace Path.root() in all writers

All 5 CBR writers now resolve scope via ClinicalScopeResolver and pass
hierarchical Path (trial/site/patient) to storeIdempotent.

Refs #120"
```

---

### Task 3: Scope-aware retrieval with ScopeDecay and TemporalDecay

**Files:**
- Create: `runtime/src/main/java/io/casehub/clinical/cbr/ClinicalCbrConfig.java`
- Modify: `runtime/src/main/java/io/casehub/clinical/cbr/AeEscalationPlanRetriever.java`
- Modify: `runtime/src/main/java/io/casehub/clinical/cbr/AeTrajectoryAlertService.java`
- Modify: `runtime/src/main/java/io/casehub/clinical/cbr/SiteEnrollmentAlertService.java`
- Test: `runtime/src/test/java/io/casehub/clinical/cbr/ClinicalCbrConfigTest.java`
- Modify: `runtime/src/test/java/io/casehub/clinical/cbr/AeEscalationPlanRetrieverTest.java`
- Modify: `runtime/src/main/resources/application.properties` (add config defaults)

**Interfaces:**
- Consumes: `ClinicalScopeResolver` from Task 1
- Produces: `ClinicalCbrConfig` — `@ApplicationScoped` bean that holds parsed `ScopeDecay` and `TemporalDecay` per domain from config properties

- [ ] **Step 1: Write ClinicalCbrConfig test**

Test parsing of config string formats: `exponential:0.7` → `ScopeDecay.Exponential(0.7)`, `halflife:90d` → `TemporalDecay.HalfLife(Duration.ofDays(90))`, `step:1.0` → `ScopeDecay.Step(1.0)`.

```java
package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.ScopeDecay;
import io.casehub.neocortex.memory.cbr.TemporalDecay;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ClinicalCbrConfigTest {

    @Test
    void parseScopeDecay_exponential() {
        ScopeDecay decay = ClinicalCbrConfig.parseScopeDecay("exponential:0.7");
        assertInstanceOf(ScopeDecay.Exponential.class, decay);
        assertEquals(0.7, ((ScopeDecay.Exponential) decay).base(), 0.001);
    }

    @Test
    void parseScopeDecay_linear() {
        ScopeDecay decay = ClinicalCbrConfig.parseScopeDecay("linear:3");
        assertInstanceOf(ScopeDecay.Linear.class, decay);
        assertEquals(3, ((ScopeDecay.Linear) decay).maxDepth());
    }

    @Test
    void parseScopeDecay_step() {
        ScopeDecay decay = ClinicalCbrConfig.parseScopeDecay("step:0.5");
        assertInstanceOf(ScopeDecay.Step.class, decay);
        assertEquals(0.5, ((ScopeDecay.Step) decay).beyondExact(), 0.001);
    }

    @Test
    void parseTemporalDecay_halflife() {
        TemporalDecay decay = ClinicalCbrConfig.parseTemporalDecay("halflife:90d");
        assertInstanceOf(TemporalDecay.HalfLife.class, decay);
        assertEquals(Duration.ofDays(90), ((TemporalDecay.HalfLife) decay).halfLife());
    }

    @Test
    void parseTemporalDecay_linear() {
        TemporalDecay decay = ClinicalCbrConfig.parseTemporalDecay("linear:365d");
        assertInstanceOf(TemporalDecay.Linear.class, decay);
        assertEquals(Duration.ofDays(365), ((TemporalDecay.Linear) decay).zeroAt());
    }
}
```

- [ ] **Step 2: Implement ClinicalCbrConfig**

`@ApplicationScoped` with `@ConfigProperty` fields for each domain's scope decay and temporal decay strings. Static parse methods for `ScopeDecay` and `TemporalDecay` from `type:value` format. Duration parsing: `Nd` → `Duration.ofDays(N)`.

- [ ] **Step 3: Update AeEscalationPlanRetriever**

Inject `ClinicalScopeResolver` and `ClinicalCbrConfig`. In `retrieve(AdverseEvent ae)`:
1. Resolve scope: `scopeResolver.forAdverseEvent(ae).orElse(Path.root())`
2. Build query with scope and decay: `CbrQuery.of(..., scope, ...).withScopeDecay(config.aeScopeDecay()).withTemporalDecay(config.aeTemporalDecay())`

Note: retriever falls back to `Path.root()` (not skip) — unlike writers, a retrieval should still work even without full scope resolution, it just won't benefit from scope-aware scoring.

- [ ] **Step 4: Update AeTrajectoryAlertService**

Same pattern: inject resolver and config, resolve patient scope, set decay on query.

- [ ] **Step 5: Update SiteEnrollmentAlertService**

Inject resolver and config. Resolve site scope via `scopeResolver.forSiteEnrollment(site)`. Set decay on query.

- [ ] **Step 6: Add config defaults to application.properties**

```properties
# CBR scope decay
casehub.clinical.cbr.scope-decay.ae=exponential:0.7
casehub.clinical.cbr.scope-decay.deviation=exponential:0.8
casehub.clinical.cbr.scope-decay.amendment=step:1.0
casehub.clinical.cbr.scope-decay.site-enrollment=exponential:0.7
casehub.clinical.cbr.scope-decay.trial-safety=step:1.0

# CBR temporal decay
casehub.clinical.cbr.temporal-decay.ae=halflife:90d
casehub.clinical.cbr.temporal-decay.ae-trajectory=halflife:60d
casehub.clinical.cbr.temporal-decay.deviation=halflife:180d
casehub.clinical.cbr.temporal-decay.amendment=halflife:365d
casehub.clinical.cbr.temporal-decay.site-enrollment=halflife:60d
casehub.clinical.cbr.temporal-decay.trial-safety=halflife:90d
```

- [ ] **Step 7: Update retriever tests**

Update test mocks and assertions to account for the new scope and decay parameters on queries.

- [ ] **Step 8: Run test suite**

Run: `mvn install -pl api --batch-mode && mvn test -pl runtime --batch-mode`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git commit -m "feat(#120): scope-aware retrieval with ScopeDecay and TemporalDecay

All 3 retrievers now construct scoped CbrQuery with configurable
scope decay and temporal decay per domain.

Refs #120"
```

---

### Task 4: Trust wiring — ClinicalAgentTrustProvider and writer trust integration

**Files:**
- Create: `runtime/src/main/java/io/casehub/clinical/cbr/ClinicalAgentTrustProvider.java`
- Modify: `runtime/src/main/java/io/casehub/clinical/cbr/ClinicalCaseOutcomeObserver.java`
- Modify: `runtime/src/main/java/io/casehub/clinical/cbr/DeviationResolutionCbrWriter.java`
- Modify: `runtime/src/main/java/io/casehub/clinical/cbr/AmendmentResolutionCbrWriter.java`
- Test: `runtime/src/test/java/io/casehub/clinical/cbr/ClinicalAgentTrustProviderTest.java`
- Modify: `runtime/src/main/resources/application.properties` (enable trust weighting)

**Interfaces:**
- Consumes: `TrustScoreSource` (casehub-ledger-api), `ClinicalTrustDimensions` (clinical api)
- Produces: `ClinicalAgentTrustProvider` implements `AgentTrustProvider`

- [ ] **Step 1: Write ClinicalAgentTrustProvider test**

Test: returns average of dimension scores when all present, returns empty when no scores, handles partial scores (some dimensions missing).

```java
package io.casehub.clinical.cbr;

import io.casehub.ledger.api.spi.TrustScoreSource;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClinicalAgentTrustProviderTest {

    @Test
    void returnsAverageOfDimensionScores() {
        TrustScoreSource source = mock(TrustScoreSource.class);
        when(source.dimensionScore("agent-1", "safety-accuracy")).thenReturn(OptionalDouble.of(0.9));
        when(source.dimensionScore("agent-1", "eligibility-precision")).thenReturn(OptionalDouble.of(0.8));
        when(source.dimensionScore("agent-1", "protocol-adherence")).thenReturn(OptionalDouble.of(0.7));

        ClinicalAgentTrustProvider provider = new ClinicalAgentTrustProvider(source);
        OptionalDouble score = provider.currentTrustScore("agent-1");

        assertTrue(score.isPresent());
        assertEquals(0.8, score.getAsDouble(), 0.001);
    }

    @Test
    void returnsEmptyWhenNoDimensionScores() {
        TrustScoreSource source = mock(TrustScoreSource.class);
        when(source.dimensionScore(anyString(), anyString())).thenReturn(OptionalDouble.empty());

        ClinicalAgentTrustProvider provider = new ClinicalAgentTrustProvider(source);
        assertTrue(provider.currentTrustScore("unknown-agent").isEmpty());
    }

    @Test
    void averagesOnlyPresentDimensions() {
        TrustScoreSource source = mock(TrustScoreSource.class);
        when(source.dimensionScore("agent-1", "safety-accuracy")).thenReturn(OptionalDouble.of(0.6));
        when(source.dimensionScore("agent-1", "eligibility-precision")).thenReturn(OptionalDouble.empty());
        when(source.dimensionScore("agent-1", "protocol-adherence")).thenReturn(OptionalDouble.of(0.8));

        ClinicalAgentTrustProvider provider = new ClinicalAgentTrustProvider(source);
        OptionalDouble score = provider.currentTrustScore("agent-1");

        assertTrue(score.isPresent());
        assertEquals(0.7, score.getAsDouble(), 0.001);
    }
}
```

- [ ] **Step 2: Implement ClinicalAgentTrustProvider**

```java
package io.casehub.clinical.cbr;

import io.casehub.clinical.api.ClinicalTrustDimensions;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.neocortex.memory.cbr.AgentTrustProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.OptionalDouble;
import java.util.stream.Stream;

@ApplicationScoped
public class ClinicalAgentTrustProvider implements AgentTrustProvider {

    private static final String[] DIMENSIONS = {
        ClinicalTrustDimensions.SAFETY_ACCURACY,
        ClinicalTrustDimensions.ELIGIBILITY_PRECISION,
        ClinicalTrustDimensions.PROTOCOL_ADHERENCE
    };

    private final TrustScoreSource trustScoreSource;

    @Inject
    public ClinicalAgentTrustProvider(TrustScoreSource trustScoreSource) {
        this.trustScoreSource = trustScoreSource;
    }

    @Override
    public OptionalDouble currentTrustScore(String agentId) {
        double[] scores = Stream.of(DIMENSIONS)
            .map(dim -> trustScoreSource.dimensionScore(agentId, dim))
            .filter(OptionalDouble::isPresent)
            .mapToDouble(OptionalDouble::getAsDouble)
            .toArray();
        if (scores.length == 0) return OptionalDouble.empty();
        double avg = 0;
        for (double s : scores) avg += s;
        return OptionalDouble.of(avg / scores.length);
    }
}
```

- [ ] **Step 3: Update writers to pass trust info**

**ClinicalCaseOutcomeObserver:** Extract `producerAgentId` from `PlanItemRecord.executorName()` for the safety-review binding. Look up trust score via `ClinicalAgentTrustProvider`. Pass both to `PlanCbrCase` constructor (8th and 9th args, replacing `null, null`).

**DeviationResolutionCbrWriter:** Pass `null` for both (PI-driven, no agent). No change to constructor call.

**AmendmentResolutionCbrWriter:** Navigate to engine case, extract executor name from amendment-advisor binding. Pass trust score. Fall back to null if no engine case.

- [ ] **Step 4: Enable trust weighting in application.properties**

```properties
casehub.cbr.trust-weighting.enabled=true
casehub.cbr.trust-weighting.influence=0.3
```

- [ ] **Step 5: Run tests**

Run: `mvn install -pl api --batch-mode && mvn test -pl runtime --batch-mode`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(#120): trust-weighted CBR — ClinicalAgentTrustProvider + writer integration

ClinicalAgentTrustProvider averages safety-accuracy, eligibility-precision,
protocol-adherence dimension scores from TrustScoreSource. Writers pass
producerAgentId and trustScore to PlanCbrCase/TextualCbrCase constructors.
Trust weighting enabled (influence=0.3).

Refs #120"
```

---

### Task 5: Cross-scope aggregation — TrialSafetyAggregationJob and DsmbSafetySignalEvent

**Files:**
- Create: `api/src/main/java/io/casehub/clinical/api/DsmbSafetySignalEvent.java`
- Modify: `runtime/src/main/java/io/casehub/clinical/cbr/ClinicalCbrDomains.java` (add TRIAL_SAFETY)
- Modify: `runtime/src/main/java/io/casehub/clinical/cbr/ClinicalCbrSchemaInitializer.java` (add trial safety schema)
- Create: `runtime/src/main/java/io/casehub/clinical/entity/TrialSafetySignal.java`
- Create: `runtime/src/main/java/io/casehub/clinical/cbr/TrialSafetyAggregationJob.java`
- Create: `runtime/src/main/java/io/casehub/clinical/ledger/DsmbSafetySignalLedgerEntry.java`
- Create: `runtime/src/main/java/io/casehub/clinical/service/DsmbSafetySignalLedgerWriter.java`
- Create: `runtime/src/main/resources/db/migration/default/V128__trial_safety_signal.sql`
- Test: `runtime/src/test/java/io/casehub/clinical/cbr/TrialSafetyAggregationJobTest.java`
- Modify: `runtime/src/test/resources/application.properties` (exclude new job from test scheduler)

**Interfaces:**
- Consumes: `ClinicalScopeResolver`, `ClinicalCbrService`, `AdverseEvent` JPA entity
- Produces: `DsmbSafetySignalEvent(UUID trialId, String signalType, List<UUID> affectedSites, String summary, String tenantId)`

- [ ] **Step 1: Create DsmbSafetySignalEvent in api/**

```java
package io.casehub.clinical.api;

import java.util.List;
import java.util.UUID;

public record DsmbSafetySignalEvent(UUID trialId, String signalType,
                                     List<UUID> affectedSites, String summary,
                                     String tenantId) {}
```

- [ ] **Step 2: Add TRIAL_SAFETY domain and schema**

Add to `ClinicalCbrDomains`: `public static final MemoryDomain TRIAL_SAFETY = new MemoryDomain("clinical-trial-safety");`

Add to `ClinicalCbrSchemaInitializer.onStartup()`: register `trialSafetySchema()`.

```java
static CbrFeatureSchema trialSafetySchema() {
    return new CbrFeatureSchema(ClinicalCbrDomains.TRIAL_SAFETY.name(), List.of(
        FeatureField.categorical("trialPhase"),
        FeatureField.numeric("aggregationPeriodDays"),
        FeatureField.numeric("siteCount"),
        FeatureField.numeric("affectedSiteCount"),
        FeatureField.numeric("dominantGrade"),
        FeatureField.categoricalList("dominantEventType"),
        FeatureField.categorical("signalType")
    ));
}
```

- [ ] **Step 3: Create Flyway migration V128**

```sql
CREATE TABLE trial_safety_signal (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    trial_id UUID NOT NULL,
    signal_type VARCHAR(50) NOT NULL,
    affected_site_count INTEGER NOT NULL,
    first_detected_at TIMESTAMP NOT NULL,
    last_detected_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,
    CONSTRAINT uq_trial_safety_signal UNIQUE (tenant_id, trial_id, signal_type)
);
```

- [ ] **Step 4: Create TrialSafetySignal JPA entity**

Panache entity in `io.casehub.clinical.entity` (default datasource — same as other clinical domain entities).

- [ ] **Step 5: Write TrialSafetyAggregationJob test**

Unit test with mock entity finders. Test: grade threshold detection (3 of 5 sites above threshold fires signal), frequency spike (2σ outlier), cross-site cluster (same event type at ≥3 sites), no signal when below thresholds, signal escalation (increased site count fires event), resolved signal (no longer detected sets resolved_at).

- [ ] **Step 6: Implement TrialSafetyAggregationJob**

`@ApplicationScoped`, `@Scheduled` (configurable interval). Follows `SiteEnrollmentTrajectoryJob` pattern: tenant ID from config, iterate active trials, query AE entities per site, compute signals, store CBR cases, upsert `TrialSafetySignal`, fire `DsmbSafetySignalEvent` on new/escalated signals.

- [ ] **Step 7: Create DsmbSafetySignalLedgerEntry, migration, and DsmbSafetySignalLedgerWriter**

`DsmbSafetySignalLedgerEntry extends LedgerEntry` in `io.casehub.clinical.ledger` (qhorus datasource). Fields: `trialId`, `signalType`, `affectedSiteCount`, `summary`. Override `domainContentBytes()`.

Flyway migration `runtime/src/main/resources/db/migration/qhorus/V2031__dsmb_safety_signal_ledger_entry.sql` — join table for the ledger entry subclass.

`DsmbSafetySignalLedgerWriter` — `@ApplicationScoped`, observes `@ObservesAsync DsmbSafetySignalEvent`. Writes the ledger entry with `entryType = EVENT`, `actorType = SYSTEM`.

- [ ] **Step 8: Exclude job from test scheduler**

Add to test `application.properties`:
```properties
quarkus.arc.exclude-types=...io.casehub.clinical.cbr.TrialSafetyAggregationJob
```

- [ ] **Step 9: Run tests**

Run: `mvn install -pl api --batch-mode && mvn test -pl runtime --batch-mode`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git commit -m "feat(#120): cross-scope aggregation — TrialSafetyAggregationJob + DsmbSafetySignalEvent

Periodic job scans AE entities per site, detects grade-threshold,
frequency-spike, and cross-site-cluster signals. Stores trial-scope
CBR cases in clinical-trial-safety domain. Fires DsmbSafetySignalEvent
on new/escalated signals. DsmbSafetySignalLedgerWriter records each
signal in the tamper-evident audit trail.

Refs #120"
```

---

### Task 6: Active memory management — supersession and retention purge

**Files:**
- Create: `runtime/src/main/java/io/casehub/clinical/cbr/AmendmentSupersessionObserver.java`
- Create: `runtime/src/main/java/io/casehub/clinical/cbr/CbrRetentionPurgeJob.java`
- Test: `runtime/src/test/java/io/casehub/clinical/cbr/AmendmentSupersessionObserverTest.java`
- Test: `runtime/src/test/java/io/casehub/clinical/cbr/CbrRetentionPurgeJobTest.java`
- Modify: `runtime/src/main/resources/application.properties` (retention config)
- Modify: `runtime/src/test/resources/application.properties` (exclude purge job)

**Interfaces:**
- Consumes: `CbrCaseMemoryStore.supersede()`, `CbrCaseMemoryStore.purge()`, `CbrRetentionPolicy`
- Produces: `AmendmentSupersessionObserver` (observes `ProtocolAmendmentResolvedEvent`), `CbrRetentionPurgeJob` (`@Scheduled` weekly)

- [ ] **Step 1: Write AmendmentSupersessionObserver test**

Test: new amendment for a trial supersedes prior amendment's CBR case. No prior amendment → no supersession call. Test uses mock `CbrCaseMemoryStore` and stub `ProtocolAmendment` entities.

- [ ] **Step 2: Implement AmendmentSupersessionObserver**

`@ApplicationScoped`. Observes `@ObservesAsync ProtocolAmendmentResolvedEvent`. Queries `ProtocolAmendment.findByTrialId(trialId)` ordered by `proposedAt`, finds the most recent prior amendment, supersedes its CBR case if one exists in the `clinical-amendment` domain.

- [ ] **Step 3: Write CbrRetentionPurgeJob test**

Test: calls `purge()` with correct policy per domain, respects config values.

- [ ] **Step 4: Implement CbrRetentionPurgeJob**

`@ApplicationScoped`, `@Scheduled` (default weekly). Reads retention config per domain from `application.properties`. Calls `store.purge(new CbrRetentionPolicy(tenantId, domain, caseType, maxAgeDays, maxCasesPerType))` for each domain.

- [ ] **Step 5: Add config defaults**

```properties
# CBR retention
casehub.clinical.cbr.retention.ae.max-age-days=730
casehub.clinical.cbr.retention.ae.max-cases=10000
casehub.clinical.cbr.retention.ae-trajectory.max-age-days=365
casehub.clinical.cbr.retention.trial-safety.max-age-days=365
```

- [ ] **Step 6: Exclude purge job from tests**

```properties
quarkus.arc.exclude-types=...io.casehub.clinical.cbr.CbrRetentionPurgeJob
```

- [ ] **Step 7: Run tests**

Run: `mvn install -pl api --batch-mode && mvn test -pl runtime --batch-mode`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git commit -m "feat(#120): active memory management — supersession + retention purge

AmendmentSupersessionObserver supersedes prior amendment CBR cases when
a new amendment resolves for the same trial. CbrRetentionPurgeJob runs
weekly with configurable max-age and max-cases per domain.

Refs #120"
```

---

### Task 7: GDPR scope isolation — CBR erasure in ConsentWithdrawalService

**Files:**
- Modify: `runtime/src/main/java/io/casehub/clinical/service/ConsentWithdrawalService.java`
- Modify: `runtime/src/test/java/io/casehub/clinical/service/ConsentWithdrawalServiceTest.java`

**Interfaces:**
- Consumes: `CbrCaseMemoryStore.eraseByScope(Path, String)`, `ClinicalScopeResolver`

- [ ] **Step 1: Write test for CBR erasure on consent withdrawal**

Add test to `ConsentWithdrawalServiceTest`: after `withdraw()`, verify `cbrCaseMemoryStore.eraseByScope()` was called with `Path.of(trialId, siteId, patientId)` and the correct tenant ID. Verify it's called before pseudonymization (before `enrollment.patientId` is overwritten).

- [ ] **Step 2: Update ConsentWithdrawalService**

Inject `CbrCaseMemoryStore`. Before the pseudonymization line (`enrollment.patientId = "erased-" + ...`):

```java
// CBR scope-based erasure — must execute before patientId pseudonymization
// because the scope path uses the original patientId
try {
    TrialSite site = TrialSite.findById(enrollment.siteId);
    if (site != null) {
        int cbrErased = cbrCaseMemoryStore.eraseByScope(
            Path.of(site.trialId.toString(), enrollment.siteId.toString(), originalPatientId),
            tenantId);
        LOG.infof("ConsentWithdrawalService: erased %d CBR cases for patient scope", cbrErased);
    }
} catch (Exception e) {
    LOG.warnf(e, "ConsentWithdrawalService: CBR scope erasure failed — ignored");
}
```

Capture `originalPatientId` before mutation: `String originalPatientId = enrollment.patientId;`

- [ ] **Step 3: Run tests**

Run: `mvn test -pl runtime -Dtest=ConsentWithdrawalServiceTest --batch-mode`
Expected: PASS

- [ ] **Step 4: Run full test suite**

Run: `mvn install -pl api --batch-mode && mvn test -pl runtime --batch-mode`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(#120): GDPR scope isolation — CBR erasure in ConsentWithdrawalService

Patient-scope CBR cases (AE, trajectory) erased before patientId
pseudonymization. Site-level and trial-level aggregate cases preserved.

Refs #120"
```

---

### Task 8: File follow-up issues and update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md` (if any new conventions established)

- [ ] **Step 1: File compaction follow-up issue**

```bash
gh issue create --repo casehubio/clinical --title "feat: CBR case compaction — merge similar cases into weighted representatives" \
  --body "## Part of\nEpic #115 — CBR roadmap.\n\n## Context\nDeferred from #120 (CBR Phase 7). Temporal decay + purge handle volume in the near term. Compaction merges N similar CBR cases into one weighted representative, reducing storage and retrieval cost.\n\n## Trigger criteria\nImplement when retrieval performance degrades or case count exceeds retention policy limits consistently.\n\n## Depends on\ncasehubio/neocortex — compaction API (does not exist yet)\n\n## Scale / Complexity\nM / Med"
```

- [ ] **Step 2: File AE regrade follow-up issue**

```bash
gh issue create --repo casehubio/clinical --title "feat: AE regrade capability — change CTCAE grade after initial assessment" \
  --body "## Part of\nEpic #115 — CBR roadmap.\n\n## Context\nDeferred from #120 (CBR Phase 7). AE regrading is a clinical workflow that does not yet exist. Includes: AeRegradedEvent, regrade service, regrade API, and CBR supersession hook (supersede original AE CBR case when grade changes).\n\n## Scale / Complexity\nL / High"
```

- [ ] **Step 3: File DSMB WorkItem follow-up issue**

```bash
gh issue create --repo casehubio/clinical --title "feat: DSMB WorkItem for batch-detected safety signals" \
  --body "## Part of\nEpic #115 — CBR roadmap.\n\n## Context\nDeferred from #120 (CBR Phase 7). WorkItem creation for batch-detected statistical trends from TrialSafetyAggregationJob. Analogous to Layer 6's DSMB WorkItem for acute Grade 4+ concurrent signals.\n\n## Depends on\nNotification infrastructure design\n\n## Scale / Complexity\nM / Med"
```

- [ ] **Step 4: Update CLAUDE.md if needed**

Add scheduler exclusion entries for new jobs if not already covered by the existing patterns.

- [ ] **Step 5: Commit workspace artifacts**

Commit the plan to the workspace repo.

```bash
git -C /Users/mdproctor/claude/casehub/worktrees/53/work/clinical add plans/2026-07-30-cbr-multi-scope-dsmb.md
git -C /Users/mdproctor/claude/casehub/worktrees/53/work/clinical commit -m "docs: CBR multi-scope DSMB implementation plan

Refs #120"
```
