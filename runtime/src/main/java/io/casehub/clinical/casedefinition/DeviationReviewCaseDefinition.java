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

/** Fluent DSL companion for {@code clinical/deviation-review.yaml}. */
public final class DeviationReviewCaseDefinition {

    private DeviationReviewCaseDefinition() {}

    public static CaseDefinition build() {
        var irbDecided = Goal.builder()
            .name("irb-decided")
            .kind(GoalKind.SUCCESS)
            .condition(".irbConsultation != null")
            .build();

        return CaseDefinition.builder()
            .namespace("clinical")
            .name("deviation-review")
            .version("1.0.0")
            .title("Protocol Deviation Review — IRB consultation gate")
            .goals(irbDecided)
            .completion(GoalExpression.allOf(irbDecided))
            .bindings(
                Binding.builder()
                    .name("irb-consultation")
                    .on(new ContextChangeTrigger(".irbConsultationRequired == true and .irbConsultation == null"))
                    .judgment(JudgmentTarget.builder()
                        .title("IRB consultation required — protocol deviation")
                        .expiresIn(Duration.ofHours(72))
                        .human(new HumanRoutingConfig(null, new io.casehub.api.spi.routing.CandidateSetSpec.Inline(StaticSetStrategy.of("irb-committee")), null, null, null))
                        .inputMapping("{ deviationId: .deviationId, severity: .severity }")
                        .outputMapping("{ irbConsultation: . }")
                        .build())
                    .build()
            )
            .build();
    }
}
