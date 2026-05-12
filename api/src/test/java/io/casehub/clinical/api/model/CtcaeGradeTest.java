package io.casehub.clinical.api.model;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class CtcaeGradeTest {

    @Test
    void grade1_has_7day_sla() {
        assertThat(CtcaeGrade.GRADE_1.sla()).hasValue(Duration.ofDays(7));
    }

    @Test
    void grade2_has_7day_sla() {
        assertThat(CtcaeGrade.GRADE_2.sla()).hasValue(Duration.ofDays(7));
    }

    @Test
    void grade3_has_24h_sla() {
        assertThat(CtcaeGrade.GRADE_3.sla()).hasValue(Duration.ofHours(24));
    }

    @Test
    void grade4_has_24h_sla() {
        assertThat(CtcaeGrade.GRADE_4.sla()).hasValue(Duration.ofHours(24));
    }

    @Test
    void grade5_has_1h_sla() {
        assertThat(CtcaeGrade.GRADE_5.sla()).hasValue(Duration.ofHours(1));
    }

    @Test
    void all_grades_have_non_empty_sla() {
        for (CtcaeGrade grade : CtcaeGrade.values()) {
            assertThat(grade.sla()).as("Grade %s must have SLA", grade).isPresent();
        }
    }

    @Test
    void grades_have_correct_ctcae_labels() {
        assertThat(CtcaeGrade.GRADE_1.label()).isEqualTo("Mild");
        assertThat(CtcaeGrade.GRADE_2.label()).isEqualTo("Moderate");
        assertThat(CtcaeGrade.GRADE_3.label()).isEqualTo("Severe");
        assertThat(CtcaeGrade.GRADE_4.label()).isEqualTo("Life-threatening");
        assertThat(CtcaeGrade.GRADE_5.label()).isEqualTo("Death");
    }
}
