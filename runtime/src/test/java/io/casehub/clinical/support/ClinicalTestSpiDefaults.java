package io.casehub.clinical.support;

import io.casehub.api.spi.ActionRiskClassifier;
import io.casehub.api.spi.CaseChannelProvider;
import io.casehub.api.spi.DispatchBudget;
import io.casehub.api.spi.FailureClassifier;
import io.casehub.api.spi.RiskDecision;
import io.casehub.api.spi.WorkerContextProvider;
import io.casehub.api.spi.WorkerProvisioner;
import io.casehub.api.spi.WorkerStatusListener;
import io.casehub.api.spi.recovery.ErrorClassifier;
import io.casehub.api.spi.routing.AgentRoutingStrategy;
import io.casehub.api.spi.routing.WorkloadDataProvider;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.eidos.api.CapabilityHealth;
import io.casehub.eidos.api.VocabularyRegistry;
import io.casehub.engine.common.spi.scheduler.WorkerExecutionRoutingStrategy;
import io.casehub.engine.internal.routing.FirstSupportedRoutingStrategy;
import io.casehub.engine.internal.routing.NoOpWorkloadDataProvider;
import io.casehub.engine.internal.worker.DefaultErrorClassifier;
import io.casehub.engine.internal.worker.DefaultFailureClassifier;
import io.casehub.engine.internal.worker.NoOpAgentRegistry;
import io.casehub.engine.internal.worker.NoOpCapabilityHealth;
import io.casehub.engine.internal.worker.NoOpCaseChannelProvider;
import io.casehub.engine.internal.worker.NoOpDispatchBudget;
import io.casehub.engine.internal.worker.NoOpVocabularyRegistry;
import io.casehub.engine.internal.worker.NoOpWorkerProvisioner;
import io.casehub.engine.internal.worker.NoOpWorkerStatusListener;
import io.casehub.engine.internal.worker.EmptyWorkerContextProvider;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI defaults for engine SPIs that lack @DefaultBean in the current SNAPSHOT.
 * Engine #1049 moved these from CDI beans (runtime) to plain POJOs (runtime-core),
 * leaving consumer test contexts without providers.
 */
@ApplicationScoped
public class ClinicalTestSpiDefaults {

    @Produces @DefaultBean
    WorkerStatusListener workerStatusListener() {
        return new NoOpWorkerStatusListener();
    }

    @Produces @DefaultBean
    CaseChannelProvider caseChannelProvider() {
        return new NoOpCaseChannelProvider();
    }

    @Produces @DefaultBean
    DispatchBudget dispatchBudget() {
        return new NoOpDispatchBudget();
    }

    @Produces @DefaultBean
    WorkerProvisioner workerProvisioner() {
        return new NoOpWorkerProvisioner();
    }

    @Produces @DefaultBean
    CapabilityHealth capabilityHealth() {
        return new NoOpCapabilityHealth();
    }

    @Produces @DefaultBean
    VocabularyRegistry vocabularyRegistry() {
        return new NoOpVocabularyRegistry();
    }

    @Produces @DefaultBean
    AgentRegistry agentRegistry() {
        return new NoOpAgentRegistry();
    }

    @Produces @DefaultBean
    WorkloadDataProvider workloadDataProvider() {
        return new NoOpWorkloadDataProvider();
    }

    @Produces @DefaultBean
    FailureClassifier failureClassifier() {
        return new DefaultFailureClassifier();
    }

    @Produces @DefaultBean
    ErrorClassifier errorClassifier() {
        return new DefaultErrorClassifier();
    }

    @Produces @DefaultBean
    WorkerExecutionRoutingStrategy workerExecutionRoutingStrategy() {
        return new FirstSupportedRoutingStrategy();
    }

    @Produces @DefaultBean
    WorkerContextProvider workerContextProvider(CaseChannelProvider channelProvider,
                                                CurrentPrincipal principal) {
        return new EmptyWorkerContextProvider(channelProvider, principal);
    }

    @Produces @DefaultBean
    ActionRiskClassifier actionRiskClassifier() {
        return (action, context) -> new RiskDecision.Autonomous();
    }

    @Produces @DefaultBean
    AgentRoutingStrategy agentRoutingStrategy() {
        return new AgentRoutingStrategy() {
            @Override
            public io.casehub.api.spi.routing.RoutingResult select(
                    io.casehub.api.spi.routing.AgentRoutingContext context,
                    java.util.List<io.casehub.api.spi.routing.AgentCandidate> candidates) {
                if (candidates.isEmpty()) {
                    return io.casehub.api.spi.routing.RoutingResult.unresolvable("no candidates");
                }
                var first = candidates.getFirst();
                return io.casehub.api.spi.routing.RoutingResult.assigned(first.workerId(), "test-default");
            }

            @Override
            public String id() {
                return "test-default";
            }
        };
    }
}
