package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.FeatureValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SiteEnrollmentTrajectoryBuilderTest {

    private SiteEnrollmentTrajectoryBuilder builder;
    private final UUID siteId = UUID.randomUUID();
    private final UUID trialId = UUID.randomUUID();
    private final Instant trialStart = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        builder = new SiteEnrollmentTrajectoryBuilder();
    }

    @Test
    void emptyEnrollments_returnsEmptyTrajectory() {
        builder.setEnrollmentQuery((site, tenant) -> List.of());
        var trajectory = builder.buildTrajectory(siteId, trialId, trialStart, "tenant-1");
        assertTrue(trajectory.isEmpty());
    }

    @Test
    void threeWeeksOfEnrollments_threeObservations() {
        builder.setEnrollmentQuery((site, tenant) -> List.of(
            trialStart.plus(1, ChronoUnit.DAYS),
            trialStart.plus(2, ChronoUnit.DAYS),
            trialStart.plus(8, ChronoUnit.DAYS),
            trialStart.plus(15, ChronoUnit.DAYS),
            trialStart.plus(16, ChronoUnit.DAYS),
            trialStart.plus(17, ChronoUnit.DAYS)
        ));
        var trajectory = builder.buildTrajectory(siteId, trialId, trialStart, "tenant-1");

        assertEquals(3, trajectory.size());
        // Week 0: 2 enrollments
        assertEquals(0.0, val(trajectory, 0, "ts"));
        assertEquals(2.0, val(trajectory, 0, "periodCount"));
        assertEquals(2.0, val(trajectory, 0, "cumulativeCount"));
        // Week 1: 1 enrollment
        assertEquals(1.0, val(trajectory, 1, "ts"));
        assertEquals(1.0, val(trajectory, 1, "periodCount"));
        assertEquals(3.0, val(trajectory, 1, "cumulativeCount"));
        // Week 2: 3 enrollments
        assertEquals(2.0, val(trajectory, 2, "ts"));
        assertEquals(3.0, val(trajectory, 2, "periodCount"));
        assertEquals(6.0, val(trajectory, 2, "cumulativeCount"));
    }

    @Test
    void singleEnrollment_oneObservation() {
        builder.setEnrollmentQuery((site, tenant) -> List.of(
            trialStart.plus(3, ChronoUnit.DAYS)
        ));
        var trajectory = builder.buildTrajectory(siteId, trialId, trialStart, "tenant-1");

        assertEquals(1, trajectory.size());
        assertEquals(0.0, val(trajectory, 0, "ts"));
        assertEquals(1.0, val(trajectory, 0, "periodCount"));
        assertEquals(1.0, val(trajectory, 0, "cumulativeCount"));
    }

    @Test
    void enrollmentBeforeTrialStart_clampedToWeekZero() {
        builder.setEnrollmentQuery((site, tenant) -> List.of(
            trialStart.minus(2, ChronoUnit.DAYS)
        ));
        var trajectory = builder.buildTrajectory(siteId, trialId, trialStart, "tenant-1");

        assertEquals(1, trajectory.size());
        assertEquals(0.0, val(trajectory, 0, "ts"));
    }

    private double val(List<java.util.Map<String, FeatureValue>> trajectory, int index, String field) {
        return ((FeatureValue.NumberVal) trajectory.get(index).get(field)).value();
    }
}
