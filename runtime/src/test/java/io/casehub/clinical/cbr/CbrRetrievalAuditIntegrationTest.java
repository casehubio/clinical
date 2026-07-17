package io.casehub.clinical.cbr;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class CbrRetrievalAuditIntegrationTest {

    @Inject ClinicalCbrService cbrService;
    @Inject CbrCaseMemoryStore store;
    @Inject FixedCurrentPrincipal principal;

    @BeforeEach
    @jakarta.transaction.Transactional
    void setUp() {
    }

    @Test
    void retrieveWithAudit_producesTraceIdAndExplanation() {
        var cbrCase = new PlanCbrCase(
            "Grade 3 Neutropenia", "Safety review: CONTINUE", "COMPLETED", 1.0,
            FeatureValue.toFeatureMap(Map.of("grade", 3, "eventType", "Neutropenia")),
            List.of());

        store.store(cbrCase, "clinical-ae", "ae-" + UUID.randomUUID(),
            ClinicalCbrDomains.AE, principal.tenancyId(), null, Path.root());

        CbrQuery query = CbrQuery.of(principal.tenancyId(), ClinicalCbrDomains.AE,
            Path.root(), "clinical-ae",
            FeatureValue.toFeatureMap(Map.of("grade", 3, "eventType", "Neutropenia")), 10)
            .withVectorWeight(0.0);

        var result = cbrService.retrieveWithAudit(query, PlanCbrCase.class,
            UUID.randomUUID(), principal.actorId());

        assertThat(result.traceId()).isNotNull();
        assertThat(result.explanation()).contains("precedent consultation");
        assertThat(result.cases()).isNotEmpty();
    }

    @Test
    void retrieveWithAudit_emptyResults_stillProducesTraceAndExplanation() {
        CbrQuery query = CbrQuery.of(principal.tenancyId(), ClinicalCbrDomains.AE,
            Path.root(), "clinical-ae", Map.of(), 10)
            .withVectorWeight(0.0);

        var result = cbrService.retrieveWithAudit(query, PlanCbrCase.class,
            UUID.randomUUID(), principal.actorId());

        assertThat(result.traceId()).isNotNull();
        assertThat(result.explanation()).contains("0 prior cases retrieved");
        assertThat(result.cases()).isEmpty();
    }
}
