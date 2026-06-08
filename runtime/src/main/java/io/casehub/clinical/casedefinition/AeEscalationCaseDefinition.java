package io.casehub.clinical.casedefinition;

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalExpression;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.HumanTaskTarget;
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
                    .humanTask(HumanTaskTarget.inline()
                        .title("Senior safety monitor review — adverse event")
                        .expiresIn(Duration.ofHours(24))
                        .candidateGroups(Set.of("senior-safety-monitors"))
                        .inputMapping("{ aeId: .aeId, grade: .grade, enrollmentId: .enrollmentId }")
                        .outputMapping("{ safetyReview: . }")
                        .build())
                    .build(),
                Binding.builder()
                    .name("dsmb-escalation")
                    .on(new ContextChangeTrigger(".requiresDsmbEscalation == true and .dsmbEscalation == null"))
                    .humanTask(HumanTaskTarget.inline()
                        .title("DSMB escalation — Grade 4+ adverse event")
                        .expiresIn(Duration.ofHours(24))
                        .candidateGroups(Set.of("dsmb"))
                        .inputMapping("{ aeId: .aeId, grade: .grade, enrollmentId: .enrollmentId }")
                        .outputMapping("{ dsmbEscalation: . }")
                        .build())
                    .build()
            )
            .build();
    }
}
