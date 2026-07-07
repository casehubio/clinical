package io.casehub.clinical.casedefinition;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.AllOfGoalExpression;
import io.casehub.api.model.Binding;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.GoalBasedCompletion;
import io.casehub.api.model.HumanTaskTarget;
import io.casehub.api.model.converter.CaseDefinitionYamlMapper;
import io.casehub.api.spi.routing.CandidateSetSpec;
import io.casehub.api.spi.routing.StaticSetStrategy;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ClinicalCaseDefinitionEquivalenceTest {

    @Test
    void deviationReview_dslMatchesYaml() throws IOException {
        var fromYaml = CaseDefinitionYamlMapper.load(
            getClass().getClassLoader().getResourceAsStream("clinical/deviation-review.yaml"));
        var fromDsl = DeviationReviewCaseDefinition.build();

        assertThat(fromDsl.getNamespace()).isEqualTo(fromYaml.getNamespace());
        assertThat(fromDsl.getName()).isEqualTo(fromYaml.getName());
        assertThat(fromDsl.getVersion()).isEqualTo(fromYaml.getVersion());
        assertThat(fromDsl.getTitle()).isEqualTo(fromYaml.getTitle());

        assertThat(fromDsl.getGoals()).hasSameSizeAs(fromYaml.getGoals());
        for (int i = 0; i < fromYaml.getGoals().size(); i++) {
            var yamlGoal = fromYaml.getGoals().get(i);
            var dslGoal = fromDsl.getGoals().get(i);
            assertThat(dslGoal.getName()).isEqualTo(yamlGoal.getName());
            assertThat(dslGoal.getKind()).isEqualTo(yamlGoal.getKind());
            assertThat(dslGoal.getCondition()).isEqualTo(yamlGoal.getCondition());
        }

        assertThat(fromDsl.getBindings()).hasSameSizeAs(fromYaml.getBindings());
        for (int i = 0; i < fromYaml.getBindings().size(); i++) {
            verifyBinding(fromDsl.getBindings().get(i), fromYaml.getBindings().get(i));
        }

        assertThat(fromDsl.getCompletion()).isInstanceOf(GoalBasedCompletion.class);
        var dslCompletion = (GoalBasedCompletion) fromDsl.getCompletion();
        assertThat(dslCompletion.getSuccess()).isInstanceOf(AllOfGoalExpression.class);
        assertThat(((AllOfGoalExpression) dslCompletion.getSuccess()).getGoals())
            .containsExactlyInAnyOrderElementsOf(
                ((AllOfGoalExpression) ((GoalBasedCompletion) fromYaml.getCompletion()).getSuccess()).getGoals());
        assertThat(dslCompletion.getFailure()).isNull();
    }

    @Test
    void aeEscalation_dslMatchesYaml() throws IOException {
        var fromYaml = CaseDefinitionYamlMapper.load(
            getClass().getClassLoader().getResourceAsStream("clinical/ae-escalation.yaml"));
        var fromDsl = AeEscalationCaseDefinition.build();

        assertThat(fromDsl.getNamespace()).isEqualTo(fromYaml.getNamespace());
        assertThat(fromDsl.getName()).isEqualTo(fromYaml.getName());
        assertThat(fromDsl.getVersion()).isEqualTo(fromYaml.getVersion());
        assertThat(fromDsl.getTitle()).isEqualTo(fromYaml.getTitle());

        assertThat(fromDsl.getGoals()).hasSameSizeAs(fromYaml.getGoals());
        for (int i = 0; i < fromYaml.getGoals().size(); i++) {
            var yamlGoal = fromYaml.getGoals().get(i);
            var dslGoal = fromDsl.getGoals().get(i);
            assertThat(dslGoal.getName()).isEqualTo(yamlGoal.getName());
            assertThat(dslGoal.getKind()).isEqualTo(yamlGoal.getKind());
            assertThat(dslGoal.getCondition()).isEqualTo(yamlGoal.getCondition());
        }

        assertThat(fromDsl.getBindings()).hasSameSizeAs(fromYaml.getBindings());
        for (int i = 0; i < fromYaml.getBindings().size(); i++) {
            verifyBinding(fromDsl.getBindings().get(i), fromYaml.getBindings().get(i));
        }

        assertThat(fromDsl.getCompletion()).isInstanceOf(GoalBasedCompletion.class);
        var dslCompletion = (GoalBasedCompletion) fromDsl.getCompletion();
        assertThat(dslCompletion.getSuccess()).isInstanceOf(AllOfGoalExpression.class);
        assertThat(((AllOfGoalExpression) dslCompletion.getSuccess()).getGoals())
            .containsExactlyInAnyOrderElementsOf(
                ((AllOfGoalExpression) ((GoalBasedCompletion) fromYaml.getCompletion()).getSuccess()).getGoals());
        assertThat(dslCompletion.getFailure()).isNull();
    }

    @Test
    void trialCoordination_dslMatchesYaml() throws IOException {
        var fromYaml = CaseDefinitionYamlMapper.load(
            getClass().getClassLoader().getResourceAsStream("clinical/trial-coordination.yaml"));
        var fromDsl = TrialCoordinationCaseDefinition.build();

        assertThat(fromDsl.getNamespace()).isEqualTo(fromYaml.getNamespace());
        assertThat(fromDsl.getName()).isEqualTo(fromYaml.getName());
        assertThat(fromDsl.getVersion()).isEqualTo(fromYaml.getVersion());
        assertThat(fromDsl.getTitle()).isEqualTo(fromYaml.getTitle());

        // No goals or completion — trial runs for its lifetime
        assertThat(fromDsl.getGoals()).isEmpty();
        assertThat(fromDsl.getCompletion()).isNull();

        assertThat(fromDsl.getBindings()).hasSameSizeAs(fromYaml.getBindings());
        for (int i = 0; i < fromYaml.getBindings().size(); i++) {
            verifyBinding(fromDsl.getBindings().get(i), fromYaml.getBindings().get(i));
        }
    }

    private static void verifyBinding(final Binding dslBinding, final Binding yamlBinding) {
        assertThat(dslBinding.getName()).isEqualTo(yamlBinding.getName());
        assertThat(dslBinding.target().getClass())
            .as("binding '%s' target type", dslBinding.getName())
            .isEqualTo(yamlBinding.target().getClass());
        assertThat(((ContextChangeTrigger) dslBinding.getOn()).getFilter())
            .isEqualTo(((ContextChangeTrigger) yamlBinding.getOn()).getFilter());
        var dslHT = (HumanTaskTarget) dslBinding.target();
        var yamlHT = (HumanTaskTarget) yamlBinding.target();
        assertThat(dslHT.title()).isEqualTo(yamlHT.title());
        assertThat(dslHT.expiresIn()).isEqualTo(yamlHT.expiresIn());
        verifyCandidateSetSpec(dslHT.candidateGroups(), yamlHT.candidateGroups(), dslBinding.getName());
        assertThat(dslHT.inputMapping()).isEqualTo(yamlHT.inputMapping());
        assertThat(dslHT.outputMapping()).isEqualTo(yamlHT.outputMapping());
    }

    private static void verifyCandidateSetSpec(CandidateSetSpec actual, CandidateSetSpec expected, String bindingName) {
        if (expected == null) {
            assertThat(actual).as("binding '%s' candidateGroups", bindingName).isNull();
            return;
        }
        assertThat(actual).as("binding '%s' candidateGroups", bindingName).isNotNull();
        assertThat(actual.getClass()).as("binding '%s' candidateGroups type", bindingName)
            .isEqualTo(expected.getClass());
        if (expected instanceof CandidateSetSpec.Inline yamlInline
            && actual instanceof CandidateSetSpec.Inline dslInline) {
            assertThat(dslInline.strategy().id()).isEqualTo(yamlInline.strategy().id());
            if (yamlInline.strategy() instanceof StaticSetStrategy yamlStatic) {
                assertThat(((StaticSetStrategy) dslInline.strategy()).values())
                    .as("binding '%s' candidateGroups values", bindingName)
                    .isEqualTo(yamlStatic.values());
            }
        }
    }
}
