package io.casehub.clinical.api;

import io.casehub.clinical.api.model.CtcaeGrade;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AeGradeChangedEventTest {

    @Test
    void isUpgrade_grade1To3_true() {
        var event = event(CtcaeGrade.GRADE_1, CtcaeGrade.GRADE_3);
        assertTrue(event.isUpgrade());
        assertFalse(event.isDowngrade());
    }

    @Test
    void isDowngrade_grade3To1_true() {
        var event = event(CtcaeGrade.GRADE_3, CtcaeGrade.GRADE_1);
        assertFalse(event.isUpgrade());
        assertTrue(event.isDowngrade());
    }

    @Test
    void sameGrade_neitherUpgradeNorDowngrade() {
        var event = event(CtcaeGrade.GRADE_2, CtcaeGrade.GRADE_2);
        assertFalse(event.isUpgrade());
        assertFalse(event.isDowngrade());
    }

    @Test
    void nullPreviousGrade_isUpgrade() {
        var event = event(null, CtcaeGrade.GRADE_1);
        assertTrue(event.isUpgrade());
        assertFalse(event.isDowngrade());
    }

    private AeGradeChangedEvent event(CtcaeGrade prev, CtcaeGrade next) {
        return new AeGradeChangedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            prev, next, Instant.now(), "test", "default");
    }
}
