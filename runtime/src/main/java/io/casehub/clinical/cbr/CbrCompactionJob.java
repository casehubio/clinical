package io.casehub.clinical.cbr;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.*;
import io.casehub.platform.api.path.Path;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class CbrCompactionJob {

    private static final Logger LOG = Logger.getLogger(CbrCompactionJob.class);

    private static final List<String> MERGE_KEY_FIELDS = List.of(
        "grade", "eventType", "trialPhase", "unexpected", "suspected");

    private static final List<String> NUMERIC_AVERAGE_FIELDS = List.of(
        "siteEnrollmentCount", "siteTargetEnrollment", "agentTrustScore");

    private static final List<String> CATEGORICAL_OUTCOME_FIELDS = List.of(
        "priorAeCount", "safetyReviewOutcome", "dsmbEscalated", "indReportFiled", "susarOversight");

    private final CbrCaseMemoryStore store;

    @ConfigProperty(name = "casehub.clinical.cbr.compaction.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "casehub.clinical.cbr.compaction.min-group-size", defaultValue = "3")
    int minGroupSize;

    @ConfigProperty(name = "casehub.clinical.cbr.compaction.tenant-id", defaultValue = "default")
    String tenantId;

    @Inject
    public CbrCompactionJob(CbrCaseMemoryStore store) {
        this.store = store;
    }

    @Scheduled(every = "${casehub.clinical.cbr.compaction.interval:168h}",
               identity = "cbr-compaction")
    public void compactAll() {
        if (!enabled) return;
        compact();
    }

    public void compact() {
        Set<String> tenants;
        try {
            tenants = store.discoverTenants(ClinicalCbrDomains.AE);
        } catch (UnsupportedOperationException e) {
            tenants = Set.of(tenantId);
        }

        for (String tenant : tenants) {
            compactTenant(tenant);
        }
    }

    private void compactTenant(String tenant) {
        Map<String, List<CaseWithFeatures>> groups = new LinkedHashMap<>();

        try {
            var query = CbrQuery.of(tenant, ClinicalCbrDomains.AE, Path.root(),
                                    "clinical-ae", Map.of(), 10000).withMinSimilarity(0.0);
            List<ScoredCbrCase<PlanCbrCase>> allCases = store.retrieveSimilar(query, PlanCbrCase.class);

            for (ScoredCbrCase<PlanCbrCase> scored : allCases) {
                String mergeKey = computeMergeKey(scored.cbrCase().features());
                CbrCaseSummary summary = new CbrCaseSummary(
                        scored.caseId(), scored.caseId(), "clinical-ae", null, null,
                        java.time.Instant.now());
                groups.computeIfAbsent(mergeKey, k -> new ArrayList<>())
                      .add(new CaseWithFeatures(summary, scored.cbrCase()));
            }
        } catch (Exception e) {
            LOG.errorf(e, "Compaction: failed to retrieve cases for tenant %s — skipping", tenant);
            return;
        }

        int totalCompacted = 0;
        for (var entry : groups.entrySet()) {
            List<CaseWithFeatures> group = entry.getValue();
            if (group.size() < minGroupSize) {continue;}

            try {
                String entityId = "compact-" + sha256Prefix(entry.getKey());

                for (CaseWithFeatures c : group) {
                    store.eraseEntity(c.summary().entityId(), tenant);
                }

                PlanCbrCase merged = createMergedRepresentative(group);
                store.store(merged, "clinical-ae", entityId,
                            ClinicalCbrDomains.AE, tenant, null, Path.root());

                totalCompacted += group.size();
                LOG.infof("Compacted %d cases into 1 representative (key=%s)", group.size(), entityId);
            } catch (Exception e) {
                LOG.errorf(e, "Compaction failed for merge key group — %d cases affected", group.size());
            }
        }

        if (totalCompacted > 0) {
            LOG.infof("Compaction complete for tenant %s: %d cases compacted into %d representatives",
                      tenant, totalCompacted, groups.values().stream().filter(g -> g.size() >= minGroupSize).count());
        }}

    private PlanCbrCase createMergedRepresentative(List<CaseWithFeatures> group) {
        Map<String, FeatureValue> merged = new LinkedHashMap<>();

        for (String field : MERGE_KEY_FIELDS) {
            merged.put(field, group.get(0).planCase().features().get(field));
        }

        merged.put("treatmentArm", majorityVote(group, "treatmentArm"));

        for (String field : NUMERIC_AVERAGE_FIELDS) {
            merged.put(field, weightedAverage(group, field));
        }

        for (String field : CATEGORICAL_OUTCOME_FIELDS) {
            merged.put(field, majorityVote(group, field));
        }

        long totalMergeCount = group.stream()
            .mapToLong(c -> getMergeCount(c.planCase()))
            .sum();
        merged.put("mergeCount", FeatureValue.number(totalMergeCount));

        double weightedConfidence = group.stream()
            .mapToDouble(c -> (c.planCase().confidence() != null ? c.planCase().confidence().value() : 1.0) * getMergeCount(c.planCase()))
            .sum() / totalMergeCount;

        CaseWithFeatures mostRecent = group.stream()
            .max(Comparator.comparing(c -> c.summary().storedAt()))
            .orElse(group.get(0));

        return new PlanCbrCase(mostRecent.planCase().problem(), mostRecent.planCase().solution(), mostRecent.planCase().outcome(), Confidence.unknown(weightedConfidence), merged, List.of(), null, null);
    }

    private FeatureValue weightedAverage(List<CaseWithFeatures> group, String field) {
        double totalWeight = 0;
        double weightedSum = 0;
        for (CaseWithFeatures c : group) {
            long weight = getMergeCount(c.planCase());
            FeatureValue fv = c.planCase().features().get(field);
            if (fv instanceof FeatureValue.NumberVal n) {
                weightedSum += n.value() * weight;
                totalWeight += weight;
            }
        }
        return totalWeight > 0 ? FeatureValue.number(weightedSum / totalWeight) : FeatureValue.number(0);
    }

    private FeatureValue majorityVote(List<CaseWithFeatures> group, String field) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (CaseWithFeatures c : group) {
            FeatureValue fv = c.planCase().features().get(field);
            if (fv == null) continue;
            long weight = getMergeCount(c.planCase());
            String key = fv.toString();
            counts.merge(key, weight, Long::sum);
        }
        if (counts.isEmpty()) return group.get(0).planCase().features().get(field);

        String winner = counts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

        for (CaseWithFeatures c : group) {
            FeatureValue fv = c.planCase().features().get(field);
            if (fv != null && fv.toString().equals(winner)) return fv;
        }
        return group.get(0).planCase().features().get(field);
    }

    private long getMergeCount(PlanCbrCase planCase) {
        FeatureValue mc = planCase.features().get("mergeCount");
        if (mc instanceof FeatureValue.NumberVal n) return (long) n.value();
        return 1;
    }

    static String computeMergeKey(Map<String, FeatureValue> features) {
        return MERGE_KEY_FIELDS.stream()
            .map(f -> f + "=" + features.getOrDefault(f, FeatureValue.string("UNKNOWN")))
            .collect(Collectors.joining("|"));
    }

    private static String sha256Prefix(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    record CaseWithFeatures(CbrCaseSummary summary, PlanCbrCase planCase) {}
}
