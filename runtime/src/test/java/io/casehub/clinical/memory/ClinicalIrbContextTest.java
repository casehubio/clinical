package io.casehub.clinical.memory;

import io.casehub.memory.Memory;
import io.casehub.memory.MemoryAttributeKeys;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalIrbContextTest {

    @Test
    void empty_returns_safe_defaults() {
        ClinicalIrbContext ctx = ClinicalIrbContext.empty();

        assertThat(ctx.totalDecisions()).isZero();
        assertThat(ctx.approvedCount()).isZero();
        assertThat(ctx.hasPrecedent()).isFalse();

        Map<String, Object> map = ctx.toContextMap();
        assertThat(map.get("totalDecisions")).isEqualTo(0);
        assertThat(map.get("approvedCount")).isEqualTo(0);
        assertThat(map.get("hasPrecedent")).isEqualTo(false);
        assertThat((List<?>) map.get("decisions")).isEmpty();
    }

    @Test
    void approved_decision_sets_hasPrecedent() {
        ClinicalIrbContext ctx = new ClinicalIrbContext(List.of(decision("APPROVED")));

        assertThat(ctx.approvedCount()).isEqualTo(1);
        assertThat(ctx.hasPrecedent()).isTrue();
    }

    @Test
    void rejected_decision_does_not_set_hasPrecedent() {
        ClinicalIrbContext ctx = new ClinicalIrbContext(List.of(decision("REJECTED")));

        assertThat(ctx.approvedCount()).isZero();
        assertThat(ctx.hasPrecedent()).isFalse();
    }

    @Test
    void totalDecisions_counts_all_outcomes() {
        ClinicalIrbContext ctx = new ClinicalIrbContext(List.of(
            decision("APPROVED"), decision("REJECTED"), decision("EXPIRED")));

        assertThat(ctx.totalDecisions()).isEqualTo(3);
        assertThat(ctx.approvedCount()).isEqualTo(1);
    }

    @Test
    void toContextMap_decisions_include_decision_siteId_createdAt() {
        UUID siteId = UUID.randomUUID();
        ClinicalIrbContext ctx = new ClinicalIrbContext(List.of(decisionFromSite("APPROVED", siteId)));

        Map<String, Object> map = ctx.toContextMap();
        List<?> recs = (List<?>) map.get("decisions");
        assertThat(recs).hasSize(1);
        Map<?, ?> rec = (Map<?, ?>) recs.get(0);
        assertThat(rec.get("decision")).isEqualTo("APPROVED");
        assertThat(rec.get("siteId")).isEqualTo(siteId.toString());
    }

    // -- helpers --

    private static Memory decision(String outcome) {
        return decisionFromSite(outcome, UUID.randomUUID());
    }

    private static Memory decisionFromSite(String outcome, UUID siteId) {
        return new Memory(
            UUID.randomUUID().toString(),
            "deviation-type:CONSENT_VIOLATION",
            ClinicalMemoryDomains.IRB,
            "test-tenant",
            null,
            "IRB " + outcome,
            Map.of(MemoryAttributeKeys.OUTCOME, outcome,
                ClinicalMemoryAttributes.SITE_ID, siteId.toString(),
                MemoryAttributeKeys.ACTOR_ID, "clinical-service"),
            Instant.now());
    }
}
