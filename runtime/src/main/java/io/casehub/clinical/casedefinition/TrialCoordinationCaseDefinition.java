package io.casehub.clinical.casedefinition;

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.HumanTaskTarget;
import java.time.Duration;
import java.util.Set;

/** Fluent DSL companion for {@code clinical/trial-coordination.yaml}. */
public final class TrialCoordinationCaseDefinition {

    private TrialCoordinationCaseDefinition() {}

    public static CaseDefinition build() {
        return CaseDefinition.builder()
            .namespace("clinical")
            .name("trial-coordination")
            .version("1.0.0")
            .title("Clinical Trial Coordination — cross-site safety monitoring")
            .bindings(
                Binding.builder()
                    .name("dsmb-rollup")
                    .on(new ContextChangeTrigger("[.grade4Active // {} | to_entries[] | select(.value == true)] | length >= 2"))
                    .humanTask(HumanTaskTarget.inline()
                        .title("DSMB review — simultaneous Grade 4+ events at multiple sites")
                        .expiresIn(Duration.ofHours(48))
                        .candidateGroups(Set.of("dsmb"))
                        .inputMapping("{ trialId: .trialId, activeSites: [.grade4Active // {} | to_entries[] | select(.value == true) | .key] }")
                        .outputMapping("{ dsmbReview: . }")
                        .build())
                    .build()
            )
            .build();
    }
}
