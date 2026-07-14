package io.casehub.clinical.support;

import io.casehub.api.model.CaseStatus;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.persistence.memory.InMemoryCaseInstanceRepository;
import io.casehub.persistence.memory.InMemoryPlanItemStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class EngineStateCleaner {

    private static final Logger LOG = Logger.getLogger(EngineStateCleaner.class);

    @Inject
    CaseInstanceCache              caseInstanceCache;
    @Inject
    InMemoryPlanItemStore          planItemStore;
    @Inject
    InMemoryCaseInstanceRepository caseInstanceRepository;
    @Inject
    io.casehub.api.engine.CaseHubRuntime caseHubRuntime;


    public void clearAll() {
        waitForUpdateComplete();
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
        caseInstanceCache.clear();
    }

    private static final java.util.Set<CaseStatus> ACTIVE_STATES = java.util.Set.of(
            CaseStatus.STARTING, CaseStatus.RUNNING, CaseStatus.WAITING, CaseStatus.SUSPENDED);

    /**
     * Graceful engine quiesce: cancel all active cases via the engine API, wait
     * for them to reach terminal state. Does NOT clear the store — store clearing
     * causes RECIPIENT_FAILURE for in-flight Vert.x handlers, which taints the
     * event bus address and fails subsequent cases on the same address.
     *
     * After cancellation, clears the store and plan items — safe because cancelled
     * cases won't re-evaluate bindings (unlike RUNNING cases, which would flood the
     * Vert.x event loop with retried capability provisioning on PlanItemStore clear).
     */
    public void cancelAllAndClear() {
        var allCases = caseInstanceCache.getAll();
        LOG.infof("cancelAllAndClear: cache has %d cases, states: %s",
                allCases.size(),
                allCases.stream().map(ci -> ci.getUuid() + "=" + ci.getState()).toList());
        var activeCases = allCases.stream()
                .filter(ci -> ACTIVE_STATES.contains(ci.getState()))
                .toList();
        for (var ci : activeCases) {
            try {
                caseHubRuntime.cancelCase(ci.getUuid());
            } catch (Exception e) {
                LOG.debugf(e, "cancelAllAndClear: cancel failed for %s — proceeding", ci.getUuid());
            }
        }
        if (!activeCases.isEmpty()) {
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                boolean allTerminal = caseInstanceCache.getAll().stream()
                        .noneMatch(ci -> ACTIVE_STATES.contains(ci.getState()));
                if (allTerminal) break;
                try { Thread.sleep(50); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        planItemStore.clear();
        clearAll();
    }


    @SuppressWarnings("unchecked")
    private void waitForUpdateComplete() {
        try {
            Field storeField = InMemoryCaseInstanceRepository.class.getDeclaredField("store");
            storeField.setAccessible(true);
            Object realInstance = getActualBeanInstance();
            if (realInstance == null) {return;}

            ConcurrentHashMap<UUID, ?> store =
                    (ConcurrentHashMap<UUID, ?>) storeField.get(realInstance);

            long deadline = System.currentTimeMillis() + 500;
            while (System.currentTimeMillis() < deadline) {
                boolean allInStore = caseInstanceCache.getAll().stream()
                                                      .filter(ci -> ci.getState() == CaseStatus.RUNNING)
                                                      .allMatch(ci -> store.containsKey(ci.getUuid()));
                if (allInStore) {break;}
                try {Thread.sleep(10);} catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            LOG.debugf(e, "waitForUpdateComplete: check failed — proceeding with clearAll()");
        }
    }

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
