package io.casehub.clinical.memory;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryAttributeKeys;

import java.util.List;
import java.util.Map;

/**
 * Cross-site AE signal context for a trial. Aggregates adverse event signals
 * written to the DRUG domain (entityId: {@code trial:{trialId}}).
 *
 * <p>Each entry represents one AE from one site. This context lets the trial
 * supervisor and DSMB rollup see the full signal picture across all sites
 * without querying each site individually.
 */
public record ClinicalDrugContext(List<Memory> aeSignals) {

    public static ClinicalDrugContext empty() {
        return new ClinicalDrugContext(List.of());
    }

    public int totalAeCount() {
        return aeSignals.size();
    }

    public int grade3PlusCount() {
        return (int) aeSignals.stream().filter(m -> {
            String grade = m.attributes().get(ClinicalMemoryAttributes.GRADE);
            if (grade == null) return false;
            try {
                return CtcaeGrade.valueOf(grade).ordinal() >= CtcaeGrade.GRADE_3.ordinal();
            } catch (IllegalArgumentException e) {
                return false;
            }
        }).count();
    }

    public boolean hasSignal() {
        return grade3PlusCount() >= 1;
    }

    public Map<String, Object> toContextMap() {
        List<Map<String, Object>> signals = aeSignals.stream()
            .map(m -> Map.<String, Object>of(
                "grade", m.attributes().getOrDefault(ClinicalMemoryAttributes.GRADE, ""),
                "siteId", m.attributes().getOrDefault(ClinicalMemoryAttributes.SITE_ID, ""),
                "outcome", m.attributes().getOrDefault(MemoryAttributeKeys.OUTCOME, ""),
                "createdAt", m.createdAt().toString()))
            .toList();
        return Map.of(
            "totalAeCount", totalAeCount(),
            "grade3PlusCount", grade3PlusCount(),
            "hasSignal", hasSignal(),
            "signals", signals);
    }
}
