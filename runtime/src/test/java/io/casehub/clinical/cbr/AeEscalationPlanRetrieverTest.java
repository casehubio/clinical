package io.casehub.clinical.cbr;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.neocortex.memory.cbr.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AeEscalationPlanRetrieverTest {

    private ClinicalCbrService cbrService;
    private PlanAdapter planAdapter;
    private AeEscalationPlanRetriever retriever;

    @BeforeEach
    void setup() {
        cbrService = mock(ClinicalCbrService.class);
        planAdapter = mock(PlanAdapter.class);
        retriever = new AeEscalationPlanRetriever(cbrService, planAdapter);
        retriever.topK = 5;
        retriever.minSimilarity = 0.4;
        retriever.setEntityResolver(new StubEntityResolver());
    }

    @Test
    void retrieve_noSimilarCases_returnsNone() {
        when(cbrService.retrieveWithAudit(any(), eq(PlanCbrCase.class), any(), any()))
                .thenReturn(new AuditedRetrievalResult<>(List.of(), "trace-1", null));

        AdverseEvent ae = buildAe(CtcaeGrade.GRADE_3);
        EscalationPlanRecommendation result = retriever.retrieve(ae);
        assertThat(result.hasRecommendation()).isFalse();
    }

    @Test
    void retrieve_withSimilarCase_adaptsAndReturns() {
        var planCase = new PlanCbrCase("problem", "solution", "COMPLETED", 1.0,
                Map.of("grade", FeatureValue.number(3)), List.of());
        var scored = new ScoredCbrCase<>(planCase, "case-1", 0.87);
        when(cbrService.retrieveWithAudit(any(), eq(PlanCbrCase.class), any(), any()))
                .thenReturn(new AuditedRetrievalResult<>(List.of(scored), "trace-1", "expl"));

        var adapted = new AdaptedPlan(List.of(new AdaptedStep("safety-review", "safety-monitoring",
                "w1", "COMPLETED", 10, Map.of(), AdaptationAction.BOOSTED, "reason")));
        when(planAdapter.adapt(eq("clinical-ae"), any(), any())).thenReturn(adapted);

        AdverseEvent ae = buildAe(CtcaeGrade.GRADE_3);
        EscalationPlanRecommendation result = retriever.retrieve(ae);
        assertThat(result.hasRecommendation()).isTrue();
        assertThat(result.retrievedCaseCount()).isEqualTo(1);
        assertThat(result.topSimilarityScore()).isEqualTo(0.87);
        assertThat(result.traceId()).isEqualTo("trace-1");
        assertThat(result.explanation()).isEqualTo("expl");
    }

    @Test
    void retrieve_cbrServiceThrows_returnsNone() {
        when(cbrService.retrieveWithAudit(any(), eq(PlanCbrCase.class), any(), any()))
                .thenThrow(new RuntimeException("CBR unavailable"));

        AdverseEvent ae = buildAe(CtcaeGrade.GRADE_3);
        EscalationPlanRecommendation result = retriever.retrieve(ae);
        assertThat(result.hasRecommendation()).isFalse();
    }

    @Test
    void retrieve_adapterThrows_returnsNone() {
        var planCase = new PlanCbrCase("problem", "solution", "COMPLETED", 1.0,
                Map.of("grade", FeatureValue.number(3)), List.of());
        var scored = new ScoredCbrCase<>(planCase, "case-1", 0.87);
        when(cbrService.retrieveWithAudit(any(), eq(PlanCbrCase.class), any(), any()))
                .thenReturn(new AuditedRetrievalResult<>(List.of(scored), "trace-1", null));
        when(planAdapter.adapt(any(), any(), any()))
                .thenThrow(new RuntimeException("adaptation failed"));

        AdverseEvent ae = buildAe(CtcaeGrade.GRADE_3);
        EscalationPlanRecommendation result = retriever.retrieve(ae);
        assertThat(result.hasRecommendation()).isFalse();
    }

    private AdverseEvent buildAe(CtcaeGrade grade) {
        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.grade = grade;
        ae.eventType = "hepatotoxicity";
        ae.unexpected = false;
        ae.suspected = false;
        ae.tenantId = "test-tenant";
        ae.enrollmentId = UUID.randomUUID();
        return ae;
    }

    private static class StubEntityResolver implements AeEscalationPlanRetriever.EntityResolver {
        @Override public PatientEnrollment findEnrollment(UUID id) { return null; }
        @Override public TrialSite findSite(UUID id) { return null; }
        @Override public ClinicalTrial findTrial(UUID id) { return null; }
        @Override public long countPriorAes(UUID enrollmentId, UUID excludeAeId) { return 0; }
    }
}
