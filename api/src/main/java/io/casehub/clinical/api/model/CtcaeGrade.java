package io.casehub.clinical.api.model;

import java.time.Duration;
import java.util.Optional;

/** CTCAE v5.0 adverse event severity grades with GCP-mandated SLA durations. */
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

    /** GCP-mandated reporting SLA. Empty for grades 1 and 2 (non-serious). */
    public Optional<Duration> sla() { return Optional.ofNullable(sla); }
}
