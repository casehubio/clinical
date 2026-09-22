package io.casehub.clinical.narrative;

import io.casehub.blocks.summarisation.narrative.DecisionNarrative;
import io.casehub.blocks.summarisation.narrative.DecisionNarrativePipeline;
import io.casehub.clinical.api.spi.ClinicalNarrativeApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class NarrativeService implements ClinicalNarrativeApi {

    private final Map<String, List<DecisionNarrative>> narrativeCache = new ConcurrentHashMap<>();

    @Inject
    public NarrativeService(DecisionNarrativePipeline pipeline) {
        pipeline.narrativeBus().subscribe(e -> true, event -> {
            var narrative = event.payload();
            narrativeCache.computeIfAbsent(narrative.caseId(), k -> new CopyOnWriteArrayList<>())
                    .add(narrative);
        });
    }

    @Override
    public List<DecisionNarrative> get(String caseId) {
        return narrativeCache.getOrDefault(caseId, List.of());
    }
}
