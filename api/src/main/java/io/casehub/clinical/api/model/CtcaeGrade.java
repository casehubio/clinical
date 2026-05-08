package io.casehub.clinical.api.model;

import java.time.Duration;
import java.util.Optional;

/**
 * CTCAE v5.0 adverse event severity grades with reporting SLA durations.
 *
 * <p>GCP ICH E6(R3) §5.17 requires SAE reporting (Grade ≥ 3) to the sponsor within 24 hours.
 * Grade 5 (death) uses a 1-hour internal SLA — stricter than ICH minimum, per product policy.
 */
public enum CtcaeGrade {
    GRADE_1("Mild",             null),
    GRADE_2("Moderate",         null),
    GRADE_3("Severe",           Duration.ofHours(24)),
    GRADE_4("Life-threatening", Duration.ofHours(24)),
    GRADE_5("Death",            Duration.ofHours(1));

    private final String label;
    private final Duration sla;

    CtcaeGrade(String label, Duration sla) {
        this.label = label;
        this.sla = sla;
    }

    /** CTCAE v5.0 human-readable grade name, e.g. "Severe". Used in reports and audit records. */
    public String label() { return label; }

    /** Reporting SLA. ICH E6(R3) §5.17 for grades 3/4; internal policy for grade 5. Empty for grades 1/2 (non-serious). */
    public Optional<Duration> sla() { return Optional.ofNullable(sla); }
}
