package io.casehub.clinical.support;

import io.casehub.api.model.CaseStatus;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.persistence.memory.InMemoryCaseInstanceRepository;
// TODO(#121): MemoryPlanItemStore class not found during test compilation — engine snapshot update?
// import io.casehub.persistence.memory.MemoryPlanItemStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-scope helper that resets engine in-memory state between test classes.
 *
 * <p>The Quarkus @QuarkusTest reuses a single Quarkus instance across all test classes.
 * Engine cases started by one test class (e.g. ClinicalLayerComplianceTest) remain in the
 * TestCaseInstanceRepository store and CaseInstanceCache. When subsequent test classes
 * (ThreeSiteShowcaseTest, ProtocolAmendmentIntegrationTest) start new cases, the handler
 * for STARTING cases can race with clearAll() in a way that causes new cases to fail.
 *
 * <p>The store is cleared FIRST (before the cache). Cases whose CaseStartedEventHandler
 * has called setState(RUNNING) but not yet called update() appear RUNNING in the cache —
 * clearing the store at that point makes their update() fail with RECIPIENT_FAILURE.
 * That failure goes to the OLD case's startCase().join() (previous test class), not to
 * new cases. New cases save() to the clean store after clearAll() and their update() succeeds.
 *
 * <p>MemoryPlanItemStore is NOT cleared: clearing it causes bindings on RUNNING cases to
 * re-evaluate, flooding the Vert.x event loop with retried capability provisioning.
 */
@ApplicationScoped
public class EngineStateCleaner {

    private static final Logger LOG = Logger.getLogger(EngineStateCleaner.class);

    @Inject CaseInstanceCache caseInstanceCache;
    // TODO(#121): MemoryPlanItemStore injection commented out — class not found during compilation
    // @Inject MemoryPlanItemStore planItemStore;
    @Inject InMemoryCaseInstanceRepository caseInstanceRepository;

    /**
     * Clears engine state: InMemoryCaseInstanceRepository.store (via reflection) then
     * CaseInstanceCache. Does NOT clear MemoryPlanItemStore.
     *
     * <p>Waits internally for cases transitioning from STARTING to RUNNING to complete their
     * CaseStartedEventHandler.update() call before clearing. Cases in the narrow
     * setState(RUNNING)→update() window are detected by checking if cache-visible RUNNING
     * cases are also present in the store.
     *
     * <p>Must be called only after the caller has already confirmed no STARTING cases remain.
     */
    public void clearAll() {
        // Wait for all RUNNING cases in the cache to also appear in the store, ensuring
        // their CaseStartedEventHandler.update() has completed before we clear.
        waitForUpdateComplete();
        // Clear store first — before cache — to avoid the setState(RUNNING) vs update() race.
        try {
            Field storeField = InMemoryCaseInstanceRepository.class.getDeclaredField("store");
            storeField.setAccessible(true);
            Object realInstance = getActualBeanInstance();
            if (realInstance != null) {
                ((ConcurrentHashMap<?, ?>) storeField.get(realInstance)).clear();
                LOG.debug("EngineStateCleaner: cleared InMemoryCaseInstanceRepository.store");
            } else {
                LOG.warn("EngineStateCleaner: could not obtain actual repository instance — store NOT cleared");
            }
        } catch (Exception e) {
            LOG.errorf(e, "EngineStateCleaner: failed to clear store");
        }
        // Clear cache after store — prevents stale entries from being served to event handlers.
        caseInstanceCache.clear();
    }

    /**
     * Waits until all cases that appear RUNNING in the cache are also present in the
     * InMemoryCaseInstanceRepository store. A case visible in the cache as RUNNING but
     * absent from the store is in the narrow setState(RUNNING)→update() transition window.
     * Clearing the store in that window causes update() to fail with RECIPIENT_FAILURE.
     * This method polls until the window is closed before proceeding with clearAll().
     */
    @SuppressWarnings("unchecked")
    private void waitForUpdateComplete() {
        try {
            Field storeField = InMemoryCaseInstanceRepository.class.getDeclaredField("store");
            storeField.setAccessible(true);
            Object realInstance = getActualBeanInstance();
            if (realInstance == null) return; // can't check — skip

            ConcurrentHashMap<java.util.UUID, ?> store =
                (ConcurrentHashMap<java.util.UUID, ?>) storeField.get(realInstance);

            // Poll until all RUNNING cache entries are also in the store.
            // The narrow window is typically < 1ms; 500ms timeout is very conservative.
            long deadline = System.currentTimeMillis() + 500;
            while (System.currentTimeMillis() < deadline) {
                boolean allInStore = caseInstanceCache.getAll().stream()
                    .filter(ci -> ci.getState() == io.casehub.api.model.CaseStatus.RUNNING)
                    .allMatch(ci -> store.containsKey(ci.getUuid()));
                if (allInStore) break;
                try { Thread.sleep(10); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        } catch (Exception e) {
            LOG.debugf(e, "waitForUpdateComplete: check failed — proceeding with clearAll()");
        }
    }

    /**
     * Obtains the actual singleton bean instance (not the CDI proxy) by calling the
     * Quarkus ArC-generated arc$delegate() method on the proxy, then verifying the
     * returned object is an InMemoryCaseInstanceRepository.
     *
     * <p>In Quarkus, the injected field is a CDI subclass-proxy. The proxy extends
     * the bean class and delegates method calls to the actual contextual singleton.
     * The proxy's OWN inherited fields contain default values, not the singleton's state.
     * arc$delegate() returns the actual singleton from the scope context.
     *
     * <p>ArC proxy naming convention (Quarkus 3.x): arc$delegate() or method containing "delegate".
     * If ArC changes this convention in a future Quarkus version, this method will return null
     * silently and clearAll() will log a WARN instead of clearing state.
     * If cross-test engine state appears to leak after a Quarkus upgrade, check this first.
     */
    private Object getActualBeanInstance() {
        try {
            Class<?> proxyClass = caseInstanceRepository.getClass();
            for (java.lang.reflect.Method m : proxyClass.getDeclaredMethods()) {
                if (m.getParameterCount() == 0
                    && (m.getName().contains("delegate") || m.getName().startsWith("arc$"))) {
                    m.setAccessible(true);
                    Object result = m.invoke(caseInstanceRepository);
                    if (result instanceof InMemoryCaseInstanceRepository r) {
                        return r;
                    }
                }
            }
        } catch (Exception e) {
            LOG.debugf(e, "getActualBeanInstance: attempt failed");
        }
        return null;
    }
}
