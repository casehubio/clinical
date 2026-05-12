package io.casehub.clinical.api.model;

import java.time.Duration;
import java.util.Optional;

/**
 * CTCAE v5.0 adverse event severity grades with reporting SLA durations.
 *
 * <p>GCP ICH E6(R3) §5.17 defines reporting SLAs per grade:
 * <ul>
 *   <li>Grade 1-2 (non-serious): 7 days</li>
 *   <li>Grade 3-4 (serious): 24 hours</li>
 *   <li>Grade 5 (death): 1 hour (internal policy — stricter than ICH minimum)</li>
 * </ul>
 */
public enum CtcaeGrade {
    GRADE_1("Mild",             Duration.ofDays(7)),
    GRADE_2("Moderate",         Duration.ofDays(7)),
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

    /** Reporting SLA per GCP ICH E6(R3) §5.17. Present for all grades. */
    public Optional<Duration> sla() { return Optional.of(sla); }
}
