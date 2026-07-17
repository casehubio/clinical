package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.CbrRetrievalTrace;
import io.casehub.neocortex.memory.cbr.ExplanationRenderer;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class ClinicalExplanationRenderer implements ExplanationRenderer {

    @Override
    public String render(CbrRetrievalTrace trace) {
        int count = trace.results().size();
        String domain = trace.query().domain().name();
        String label = domainLabel(domain);

        StringBuilder sb = new StringBuilder();
        sb.append(label).append(": ");
        sb.append(count).append(count == 1 ? " prior case retrieved" : " prior cases retrieved");
        sb.append(" (min similarity ").append(String.format("%.2f", trace.query().minSimilarity())).append(").");

        if (!trace.results().isEmpty()) {
            var top = trace.results().getFirst();
            sb.append("\nTop precedent: score ").append(String.format("%.2f", top.score()));
            if (top.confidence() != null) {
                sb.append(", confidence ").append(String.format("%.2f", top.confidence()));
            }
            sb.append(".");

            if (!top.featureSimilarities().isEmpty()) {
                String breakdown = top.featureSimilarities().entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .map(e -> e.getKey() + "=" + String.format("%.2f", e.getValue()))
                    .collect(Collectors.joining(", "));
                sb.append(" Feature alignment: ").append(breakdown).append(".");
            }

            long withConfidence = trace.results().stream()
                .filter(r -> r.confidence() != null && r.confidence() >= 0.70)
                .count();
            long withoutConfidence = trace.results().stream()
                .filter(r -> r.confidence() == null)
                .count();

            sb.append("\nConfidence band: ");
            sb.append(withConfidence).append(" of ").append(count);
            sb.append(withConfidence == 1 ? " precedent has" : " precedents have");
            sb.append(" confidence >= 0.70.");
            if (withoutConfidence > 0) {
                sb.append(" ").append(withoutConfidence);
                sb.append(withoutConfidence == 1 ? " has" : " have");
                sb.append(" no recorded confidence.");
            }
        }

        sb.append("\nQuery domain: ").append(domain);
        sb.append(". Retrieval mode: ").append(trace.query().retrievalMode()).append(".");

        return sb.toString();
    }

    private static String domainLabel(String domain) {
        return switch (domain) {
            case "clinical-ae" -> "Adverse event precedent consultation";
            case "clinical-deviation" -> "Protocol deviation precedent consultation";
            case "clinical-amendment" -> "Protocol amendment precedent consultation";
            default -> "Precedent consultation (" + domain + ")";
        };
    }
}
