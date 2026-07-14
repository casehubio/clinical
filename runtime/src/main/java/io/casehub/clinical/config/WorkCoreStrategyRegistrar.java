package io.casehub.clinical.config;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.StartupEvent;
import io.casehub.engine.internal.routing.EngineStrategyResolver;
import io.casehub.work.core.policy.ContinuationPolicy;
import io.casehub.work.core.strategy.LeastLoadedStrategy;

/**
 * Registers casehub-work-core NamedStrategy beans that ArC's
 * Instance&lt;NamedStrategy&gt; fails to discover transitively.
 */
@Unremovable
@ApplicationScoped
public class WorkCoreStrategyRegistrar {

    @Inject EngineStrategyResolver strategyResolver;
    @Inject ContinuationPolicy continuationPolicy;
    @Inject LeastLoadedStrategy leastLoadedStrategy;

    void onStartup(@Observes @Priority(1) StartupEvent ev) {
        strategyResolver.registerEntry(continuationPolicy, false);
        strategyResolver.registerEntry(leastLoadedStrategy, false);
    }
}
