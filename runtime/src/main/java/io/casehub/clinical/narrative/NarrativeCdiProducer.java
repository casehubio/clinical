package io.casehub.clinical.narrative;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.narrative.DecisionNarrativePipeline;
import io.casehub.blocks.summarisation.narrative.DecisionSignal;
import io.casehub.platform.agent.AgentProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class NarrativeCdiProducer {

    @Produces
    @ApplicationScoped
    DecisionNarrativePipeline pipeline(AgentProvider agentProvider) {
        return new DecisionNarrativePipeline(agentProvider);
    }

    @Produces
    @ApplicationScoped
    EventStreamBus<DecisionSignal> signalBus(DecisionNarrativePipeline pipeline) {
        return pipeline.signalBus();
    }
}
