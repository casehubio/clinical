package io.casehub.clinical.api.model;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class CtcaeGradeTest {

    @Test
    void grade1_and_2_have_no_sla() {
        assertThat(CtcaeGrade.GRADE_1.sla()).isEmpty();
        assertThat(CtcaeGrade.GRADE_2.sla()).isEmpty();
    }

    @Test
    void grade3_and_4_have_24h_sla() {
        assertThat(CtcaeGrade.GRADE_3.sla()).contains(Duration.ofHours(24));
        assertThat(CtcaeGrade.GRADE_4.sla()).contains(Duration.ofHours(24));
    }

    @Test
    void grade5_has_1h_sla() {
        assertThat(CtcaeGrade.GRADE_5.sla()).contains(Duration.ofHours(1));
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
