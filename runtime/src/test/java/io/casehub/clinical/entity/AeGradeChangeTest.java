package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.casehub.clinical.api.ClinicalGroups.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {SPONSOR, INVESTIGATOR, COORDINATOR})
class AeGradeChangeTest {

    private UUID aeId;

    @BeforeEach
    @Transactional
    void setup() {
        AeGradeChange.deleteAll();
        aeId = UUID.randomUUID();
    }

    @Test
    @Transactional
    void findByAdverseEventId_returnsOrderedHistory() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-02T00:00:00Z");
        Instant t3 = Instant.parse("2026-01-03T00:00:00Z");

        persistChange(aeId, null, CtcaeGrade.GRADE_1, t1);
        persistChange(aeId, CtcaeGrade.GRADE_1, CtcaeGrade.GRADE_3, t3);
        persistChange(aeId, CtcaeGrade.GRADE_1, CtcaeGrade.GRADE_2, t2);

        List<AeGradeChange> history = AeGradeChange.findByAdverseEventId(aeId);
        assertEquals(3, history.size());
        assertNull(history.get(0).previousGrade);
        assertEquals(CtcaeGrade.GRADE_2, history.get(1).newGrade);
        assertEquals(CtcaeGrade.GRADE_3, history.get(2).newGrade);
    }

    @Test
    @Transactional
    void findByAdverseEventId_emptyForUnknownId() {
        assertTrue(AeGradeChange.findByAdverseEventId(UUID.randomUUID()).isEmpty());
    }

    @Test
    @Transactional
    void findLatestByAdverseEventId_returnsMostRecent() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-02T00:00:00Z");

        persistChange(aeId, null, CtcaeGrade.GRADE_1, t1);
        persistChange(aeId, CtcaeGrade.GRADE_1, CtcaeGrade.GRADE_3, t2);

        AeGradeChange latest = AeGradeChange.findLatestByAdverseEventId(aeId);
        assertNotNull(latest);
        assertEquals(CtcaeGrade.GRADE_3, latest.newGrade);
    }

    @Test
    @Transactional
    void findLatestByAdverseEventId_nullForUnknownId() {
        assertNull(AeGradeChange.findLatestByAdverseEventId(UUID.randomUUID()));
    }

    private void persistChange(UUID adverseEventId, CtcaeGrade prev, CtcaeGrade next, Instant at) {
        AeGradeChange gc = new AeGradeChange();
        gc.id = UUID.randomUUID();
        gc.adverseEventId = adverseEventId;
        gc.previousGrade = prev;
        gc.newGrade = next;
        gc.changedAt = at;
        gc.changedBy = "test";
        gc.reason = "test reason";
        gc.persist();
    }
}
