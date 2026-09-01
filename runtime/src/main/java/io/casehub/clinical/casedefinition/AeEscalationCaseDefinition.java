package io.casehub.clinical.casedefinition;

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalExpression;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.HumanRoutingConfig;
import io.casehub.api.model.JudgmentTarget;
import io.casehub.api.spi.routing.CandidateSetSpec;
import io.casehub.api.spi.routing.StaticSetStrategy;
import java.time.Duration;
import java.util.Set;

/** Fluent DSL companion for {@code clinical/ae-escalation.yaml}. */
public final class AeEscalationCaseDefinition {

    private AeEscalationCaseDefinition() {}

    public static CaseDefinition build() {
        var safetyReviewComplete = Goal.builder()
            .name("safety-review-complete")
            .kind(GoalKind.SUCCESS)
            .condition(".safetyReview != null")
            .build();

        var dsmbComplete = Goal.builder()
            .name("dsmb-complete")
            .kind(GoalKind.SUCCESS)
            .condition(".requiresDsmbEscalation == false or .dsmbEscalation != null")
            .build();

        return CaseDefinition.builder()
            .namespace("clinical")
            .name("ae-escalation")
            .version("1.0.0")
            .title("Adverse Event Safety Escalation — adaptive severity routing")
            .goals(safetyReviewComplete, dsmbComplete)
            .completion(GoalExpression.allOf(safetyReviewComplete, dsmbComplete))
            .bindings(
                Binding.builder()
                    .name("safety-review")
                    .on(new ContextChangeTrigger(".requiresSeniorMonitor == true and .safetyReview == null"))
                    .judgment(JudgmentTarget.builder()
                        .title("Senior safety monitor review — adverse event")
                        .expiresIn(Duration.ofHours(24))
                        .human(new HumanRoutingConfig(null, new CandidateSetSpec.Inline(StaticSetStrategy.of("senior-safety-monitors")), null, null, null))
                        .inputMapping("{ aeId: .aeId, grade: .grade, enrollmentId: .enrollmentId }")
                        .outputMapping("{ safetyReview: . }")
                        .build())
                    .build(),
                Binding.builder()
                    .name("dsmb-escalation")
                    .on(new ContextChangeTrigger(".requiresDsmbEscalation == true and .dsmbEscalation == null"))
                    .judgment(JudgmentTarget.builder()
                        .title("DSMB escalation — Grade 4+ adverse event")
                        .expiresIn(Duration.ofHours(24))
                        .human(new HumanRoutingConfig(null, new CandidateSetSpec.Inline(StaticSetStrategy.of("dsmb")), null, null, null))
                        .inputMapping("{ aeId: .aeId, grade: .grade, enrollmentId: .enrollmentId }")
                        .outputMapping("{ dsmbEscalation: . }")
                        .build())
                    .build()
            )
            .build();
    }
}
