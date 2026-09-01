package io.casehub.clinical.cbr;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CbrCompactionJobTest {

    private CbrCaseMemoryStore store;
    private CbrCompactionJob job;

    @BeforeEach
    void setup() {
        store = mock(CbrCaseMemoryStore.class);
        job = new CbrCompactionJob(store);
        job.tenantId = "default";
        job.minGroupSize = 3;
        job.enabled = true;
    }

    @Test
    void compact_threeMatchingCases_mergedIntoOne() {
        when(store.discoverTenants(ClinicalCbrDomains.AE)).thenReturn(Set.of("default"));

        var sameMergeKey = makeFeatures(3, "Neutropenia", "PHASE_III", 40, 0.8);
        when(store.retrieveSimilar(any(), eq(PlanCbrCase.class)))
            .thenReturn(List.of(
                scored("case-1", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), sameMergeKey, List.of(), null, null)),
                scored("case-2", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(0.9), withNumerics(sameMergeKey, 50, 0.7), List.of(), null, null)),
                scored("case-3", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(0.8), withNumerics(sameMergeKey, 60, 0.6), List.of(), null, null))
            ));

        job.compact();

        verify(store, times(3)).eraseEntity(anyString(), eq("default"));
        verify(store, times(1)).store(any(), eq("clinical-ae"), argThat(id -> id.startsWith("compact-")),
            eq(ClinicalCbrDomains.AE), eq("default"), isNull(), any());
    }

    @Test
    void compact_belowThreshold_noCompaction() {
        when(store.discoverTenants(ClinicalCbrDomains.AE)).thenReturn(Set.of("default"));

        var features = makeFeatures(3, "Neutropenia", "PHASE_III", 40, 0.8);
        when(store.retrieveSimilar(any(), eq(PlanCbrCase.class)))
            .thenReturn(List.of(
                scored("case-1", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), features, List.of(), null, null)),
                scored("case-2", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), features, List.of(), null, null))
            ));

        job.compact();

        verify(store, never()).store(any(), any(), any(), any(), any(), any(), any());
        verify(store, never()).eraseEntity(any(), any());
    }

    @Test
    void compact_differentMergeKeys_handledIndependently() {
        when(store.discoverTenants(ClinicalCbrDomains.AE)).thenReturn(Set.of("default"));

        var keyA = makeFeatures(3, "Neutropenia", "PHASE_III", 40, 0.8);
        var keyB = makeFeatures(4, "Hepatotoxicity", "PHASE_II", 20, 0.6);

        when(store.retrieveSimilar(any(), eq(PlanCbrCase.class)))
            .thenReturn(List.of(
                scored("a1", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), keyA, List.of(), null, null)),
                scored("a2", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), keyA, List.of(), null, null)),
                scored("a3", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), keyA, List.of(), null, null)),
                scored("b1", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), keyB, List.of(), null, null)),
                scored("b2", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), keyB, List.of(), null, null)),
                scored("b3", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), keyB, List.of(), null, null))
            ));

        job.compact();

        verify(store, times(6)).eraseEntity(anyString(), eq("default"));
        verify(store, times(2)).store(any(), eq("clinical-ae"), argThat(id -> id.startsWith("compact-")),
            eq(ClinicalCbrDomains.AE), eq("default"), isNull(), any());
    }

    @Test
    void compact_recompaction_weightsByMergeCount() {
        when(store.discoverTenants(ClinicalCbrDomains.AE)).thenReturn(Set.of("default"));

        var baseFeatures = makeFeatures(3, "Neutropenia", "PHASE_III", 40, 0.8);
        var compactFeatures = new LinkedHashMap<>(baseFeatures);
        compactFeatures.put("mergeCount", FeatureValue.number(5));

        var singleFeatures = new LinkedHashMap<>(baseFeatures);
        singleFeatures.put("agentTrustScore", FeatureValue.number(0.6));

        when(store.retrieveSimilar(any(), eq(PlanCbrCase.class)))
            .thenReturn(List.of(
                scored("compact-abc", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(0.9), compactFeatures, List.of(), null, null)),
                scored("new-1", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(0.7), singleFeatures, List.of(), null, null)),
                scored("new-2", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(0.7), singleFeatures, List.of(), null, null))
            ));

        job.compact();

        ArgumentCaptor<CbrCase> captor = ArgumentCaptor.forClass(CbrCase.class);
        verify(store).store(captor.capture(), eq("clinical-ae"), anyString(),
            eq(ClinicalCbrDomains.AE), eq("default"), isNull(), any());

        PlanCbrCase merged = (PlanCbrCase) captor.getValue();
        assertThat(((FeatureValue.NumberVal) merged.features().get("mergeCount")).value()).isEqualTo(7.0);

        double expectedTrust = (5 * 0.8 + 1 * 0.6 + 1 * 0.6) / 7.0;
        assertThat(((FeatureValue.NumberVal) merged.features().get("agentTrustScore")).value())
            .isCloseTo(expectedTrust, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void compact_entityId_isDeterministic() {
        when(store.discoverTenants(ClinicalCbrDomains.AE)).thenReturn(Set.of("default"));

        var features = makeFeatures(3, "Neutropenia", "PHASE_III", 40, 0.8);
        when(store.retrieveSimilar(any(), eq(PlanCbrCase.class)))
            .thenReturn(List.of(
                scored("c1", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), features, List.of(), null, null)),
                scored("c2", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), features, List.of(), null, null)),
                scored("c3", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), features, List.of(), null, null))
            ));

        job.compact();
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).store(any(), any(), idCaptor.capture(), any(), any(), any(), any());
        String firstId = idCaptor.getValue();

        reset(store);
        when(store.discoverTenants(ClinicalCbrDomains.AE)).thenReturn(Set.of("default"));
        when(store.retrieveSimilar(any(), eq(PlanCbrCase.class)))
            .thenReturn(List.of(
                scored("c1", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), features, List.of(), null, null)),
                scored("c2", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), features, List.of(), null, null)),
                scored("c3", new PlanCbrCase("p", "s", "COMPLETED", Confidence.unknown(1.0), features, List.of(), null, null))
            ));

        job.compact();
        verify(store).store(any(), any(), idCaptor.capture(), any(), any(), any(), any());

        assertThat(idCaptor.getValue()).isEqualTo(firstId);
    }

    @Test
    void compactAll_disabled_doesNothing() {
        job.enabled = false;
        job.compactAll();
        verify(store, never()).discoverTenants(any());
    }

    private static <C extends CbrCase> ScoredCbrCase<C> scored(String caseId, C cbrCase) {
        return new ScoredCbrCase<>(cbrCase, caseId, 1.0);
    }

    private Map<String, FeatureValue> makeFeatures(int grade, String eventType, String phase, long enrollment, double trust) {
        var m = new LinkedHashMap<String, FeatureValue>();
        m.put("grade", FeatureValue.number(grade));
        m.put("eventType", FeatureValue.stringList(List.of(eventType)));
        m.put("trialPhase", FeatureValue.string(phase));
        m.put("unexpected", FeatureValue.string("true"));
        m.put("suspected", FeatureValue.string("true"));
        m.put("treatmentArm", FeatureValue.string("ARM_A"));
        m.put("priorAeCount", FeatureValue.string("MULTIPLE"));
        m.put("safetyReviewOutcome", FeatureValue.string("CONTINUE"));
        m.put("dsmbEscalated", FeatureValue.string("false"));
        m.put("indReportFiled", FeatureValue.string("false"));
        m.put("susarOversight", FeatureValue.string("false"));
        m.put("siteEnrollmentCount", FeatureValue.number(enrollment));
        m.put("siteTargetEnrollment", FeatureValue.number(100));
        m.put("agentTrustScore", FeatureValue.number(trust));
        return m;
    }

    private Map<String, FeatureValue> withNumerics(Map<String, FeatureValue> base, long enrollment, double trust) {
        var m = new LinkedHashMap<>(base);
        m.put("siteEnrollmentCount", FeatureValue.number(enrollment));
        m.put("agentTrustScore", FeatureValue.number(trust));
        return m;
    }
}
