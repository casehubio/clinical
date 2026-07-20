package io.casehub.clinical.cbr;

import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiFunction;

@ApplicationScoped
public class SiteEnrollmentTrajectoryBuilder {

    private BiFunction<UUID, String, List<Instant>> enrollmentQuery = SiteEnrollmentTrajectoryBuilder::defaultQuery;

    void setEnrollmentQuery(BiFunction<UUID, String, List<Instant>> query) {
        this.enrollmentQuery = query;
    }

    public List<Map<String, FeatureValue>> buildTrajectory(UUID siteId, UUID trialId,
                                                            Instant trialActivatedAt, String tenantId) {
        List<Instant> enrollmentDates = enrollmentQuery.apply(siteId, tenantId);
        if (enrollmentDates.isEmpty()) return List.of();

        Map<Long, Integer> weekCounts = new TreeMap<>();
        for (Instant date : enrollmentDates) {
            long week = Duration.between(trialActivatedAt, date).toDays() / 7;
            if (week < 0) week = 0;
            weekCounts.merge(week, 1, Integer::sum);
        }

        List<Map<String, FeatureValue>> observations = new ArrayList<>();
        int cumulative = 0;
        for (var entry : weekCounts.entrySet()) {
            cumulative += entry.getValue();
            observations.add(Map.of(
                "ts", FeatureValue.number(entry.getKey()),
                "periodCount", FeatureValue.number(entry.getValue()),
                "cumulativeCount", FeatureValue.number(cumulative)));
        }
        return observations;
    }

    @SuppressWarnings("unchecked")
    private static List<Instant> defaultQuery(UUID siteId, String tenantId) {
        return PatientEnrollment.<PatientEnrollment>find("siteId = ?1 AND tenantId = ?2", siteId, tenantId)
                .stream()
                .filter(e -> e.enrolledAt != null)
                .map(e -> e.enrolledAt)
                .sorted()
                .toList();
    }
}
