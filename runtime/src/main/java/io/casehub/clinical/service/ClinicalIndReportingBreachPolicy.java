package io.casehub.clinical.service;

import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.api.SlaBreachPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;

/**
 * SlaBreachPolicy for IND expedited safety report filing WorkItems.
 *
 * <p>CDI: @ApplicationScoped (no @DefaultBean) — displaces NoOpSlaBreachPolicy @DefaultBean.
 * ExpiryLifecycleService injects SlaBreachPolicy as a singular @Inject point.
 *
 * <p>Pure: makes a decision and returns. No CDI calls, no DB queries, no side effects.
 *
 * <p>Stateless two-tier escalation via candidateGroups. After EscalateTo executes,
 * ExpiryLifecycleService replaces item.candidateGroups with the escalation group —
 * "regulatory-affairs" is gone on the second breach. Both groups must be tested
 * to identify regulatory WorkItems across both tiers.
 */
@ApplicationScoped
public class ClinicalIndReportingBreachPolicy implements SlaBreachPolicy {

    @Override
    public BreachDecision onBreach(SlaBreachContext ctx) {
        boolean isRegulatory = ctx.task().candidateGroups().contains("regulatory-affairs")
                || ctx.task().candidateGroups().contains("regulatory-leadership");
        if (!isRegulatory) {
            return new BreachDecision.Fail("no-sla-breach-policy-configured");
        }
        if (ctx.task().candidateGroups().contains("regulatory-leadership")) {
            return new BreachDecision.Exhausted(
                    "IND reporting deadline exhausted — operator intervention required");
        }
        return BreachDecision.EscalateTo.to("regulatory-leadership")
                .withDeadline(Duration.ofHours(48));
    }
}
