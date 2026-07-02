package io.casehub.clinical.memory;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryAttributeKeys;

import java.util.List;
import java.util.Map;

/**
 * IRB decision precedent for a specific deviation type.
 * Aggregates all IRB decisions written to the IRB domain
 * (entityId: {@code deviation-type:{deviationType}}).
 *
 * <p>Lets the IRB consultation agent reason about precedent:
 * "For CONSENT_VIOLATION, the committee has approved X times — approve here too."
 */
public record ClinicalIrbContext(List<Memory> decisions) {

    public static ClinicalIrbContext empty() {
        return new ClinicalIrbContext(List.of());
    }

    public int totalDecisions() {
        return decisions.size();
    }

    public int approvedCount() {
        return (int) decisions.stream()
            .filter(m -> "APPROVED".equals(m.attributes().get(MemoryAttributeKeys.OUTCOME)))
            .count();
    }

    public boolean hasPrecedent() {
        return approvedCount() >= 1;
    }

    public Map<String, Object> toContextMap() {
        List<Map<String, Object>> records = decisions.stream()
            .map(m -> Map.<String, Object>of(
                "decision", m.attributes().getOrDefault(MemoryAttributeKeys.OUTCOME, ""),
                "siteId", m.attributes().getOrDefault(ClinicalMemoryAttributes.SITE_ID, ""),
                "createdAt", m.createdAt().toString()))
            .toList();
        return Map.of(
            "totalDecisions", totalDecisions(),
            "approvedCount", approvedCount(),
            "hasPrecedent", hasPrecedent(),
            "decisions", records);
    }
}
