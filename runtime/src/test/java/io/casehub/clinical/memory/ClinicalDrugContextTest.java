package io.casehub.clinical.memory;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.memory.Memory;
import io.casehub.memory.MemoryAttributeKeys;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalDrugContextTest {

    @Test
    void empty_returns_safe_defaults() {
        ClinicalDrugContext ctx = ClinicalDrugContext.empty();

        assertThat(ctx.totalAeCount()).isZero();
        assertThat(ctx.grade3PlusCount()).isZero();
        assertThat(ctx.hasSignal()).isFalse();

        Map<String, Object> map = ctx.toContextMap();
        assertThat(map.get("totalAeCount")).isEqualTo(0);
        assertThat(map.get("grade3PlusCount")).isEqualTo(0);
        assertThat(map.get("hasSignal")).isEqualTo(false);
        assertThat((List<?>) map.get("signals")).isEmpty();
    }

    @Test
    void grade2_not_counted_as_serious_signal() {
        ClinicalDrugContext ctx = new ClinicalDrugContext(List.of(signal(CtcaeGrade.GRADE_2)));

        assertThat(ctx.grade3PlusCount()).isZero();
        assertThat(ctx.hasSignal()).isFalse();
    }

    @Test
    void grade3_counts_as_serious_signal() {
        ClinicalDrugContext ctx = new ClinicalDrugContext(List.of(signal(CtcaeGrade.GRADE_3)));

        assertThat(ctx.grade3PlusCount()).isEqualTo(1);
        assertThat(ctx.hasSignal()).isTrue();
    }

    @Test
    void grade5_counts_as_serious_signal() {
        ClinicalDrugContext ctx = new ClinicalDrugContext(List.of(signal(CtcaeGrade.GRADE_5)));

        assertThat(ctx.grade3PlusCount()).isEqualTo(1);
        assertThat(ctx.hasSignal()).isTrue();
    }

    @Test
    void totalAeCount_includes_all_grades() {
        ClinicalDrugContext ctx = new ClinicalDrugContext(List.of(
            signal(CtcaeGrade.GRADE_1),
            signal(CtcaeGrade.GRADE_3),
            signal(CtcaeGrade.GRADE_4)));

        assertThat(ctx.totalAeCount()).isEqualTo(3);
        assertThat(ctx.grade3PlusCount()).isEqualTo(2);
    }

    @Test
    void toContextMap_signals_include_grade_siteId_outcome_createdAt() {
        UUID siteId = UUID.randomUUID();
        ClinicalDrugContext ctx = new ClinicalDrugContext(List.of(signalFromSite(CtcaeGrade.GRADE_3, siteId)));

        Map<String, Object> map = ctx.toContextMap();
        List<?> signals = (List<?>) map.get("signals");
        assertThat(signals).hasSize(1);
        Map<?, ?> s = (Map<?, ?>) signals.get(0);
        assertThat(s.get("grade")).isEqualTo("GRADE_3");
        assertThat(s.get("siteId")).isEqualTo(siteId.toString());
        assertThat(s.get("outcome")).isEqualTo("REPORTED");
    }

    // -- helpers --

    private static Memory signal(CtcaeGrade grade) {
        return signalFromSite(grade, UUID.randomUUID());
    }

    private static Memory signalFromSite(CtcaeGrade grade, UUID siteId) {
        return new Memory(
            UUID.randomUUID().toString(),
            "trial:" + UUID.randomUUID(),
            ClinicalMemoryDomains.DRUG,
            "test-tenant",
            null,
            "AE signal",
            Map.of(ClinicalMemoryAttributes.GRADE, grade.name(),
                ClinicalMemoryAttributes.SITE_ID, siteId.toString(),
                MemoryAttributeKeys.OUTCOME, "REPORTED",
                MemoryAttributeKeys.ACTOR_ID, "clinical-service"),
            Instant.now());
    }
}
