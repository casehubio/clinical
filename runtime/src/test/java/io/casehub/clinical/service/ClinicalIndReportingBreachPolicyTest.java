package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachType;
import io.casehub.work.api.BreachedTask;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.platform.api.path.Path;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClinicalIndReportingBreachPolicyTest {

    ClinicalIndReportingBreachPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ClinicalIndReportingBreachPolicy();
    }

    @Test
    void regulatory_affairs_first_breach_escalates_to_regulatory_leadership_48h() {
        SlaBreachContext ctx = ctx(Set.of("regulatory-affairs"));
        BreachDecision decision = policy.onBreach(ctx);
        assertThat(decision).isInstanceOf(BreachDecision.EscalateTo.class);
        BreachDecision.EscalateTo escalate = (BreachDecision.EscalateTo) decision;
        assertThat(escalate.groups()).containsExactly("regulatory-leadership");
        assertThat(escalate.deadline()).isEqualTo(Duration.ofHours(48));
    }

    @Test
    void regulatory_leadership_second_breach_is_exhausted() {
        // After EscalateTo fires, ExpiryLifecycleService replaces candidateGroups —
        // "regulatory-affairs" is gone on second breach
        SlaBreachContext ctx = ctx(Set.of("regulatory-leadership"));
        BreachDecision decision = policy.onBreach(ctx);
        assertThat(decision).isInstanceOf(BreachDecision.Exhausted.class);
        BreachDecision.Exhausted exhausted = (BreachDecision.Exhausted) decision;
        assertThat(exhausted.reason()).contains("IND reporting deadline exhausted");
    }

    @Test
    void non_regulatory_workitem_returns_fail() {
        SlaBreachContext ctx = ctx(Set.of("senior-safety-monitors"));
        BreachDecision decision = policy.onBreach(ctx);
        assertThat(decision).isInstanceOf(BreachDecision.Fail.class);
        BreachDecision.Fail fail = (BreachDecision.Fail) decision;
        assertThat(fail.reason()).isEqualTo("no-sla-breach-policy-configured");
    }

    @Test
    void empty_groups_returns_fail() {
        SlaBreachContext ctx = ctx(Set.of());
        BreachDecision decision = policy.onBreach(ctx);
        assertThat(decision).isInstanceOf(BreachDecision.Fail.class);
    }

    private SlaBreachContext ctx(Set<String> candidateGroups) {
        BreachedTask task = new BreachedTask(UUID.randomUUID(), null, "Test Task", candidateGroups);
        return new SlaBreachContext(BreachType.COMPLETION_EXPIRED, task, Path.root(), null);
    }
}
