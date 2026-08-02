package io.casehub.clinical.routing;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.spi.routing.AgentRoutingContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalRoutingFeatureExtractorTest {

    private final ClinicalRoutingFeatureExtractor extractor = new ClinicalRoutingFeatureExtractor();

    private AgentRoutingContext contextWith(ObjectNode caseContext) {
        return new AgentRoutingContext(UUID.randomUUID(), "safety-monitoring", caseContext, "tenant-1", List.of(), null, null);
    }

    @Test
    void extractFeatures_fullContext_returnsAllFeatures() {
        var node = JsonNodeFactory.instance.objectNode();
        node.put("grade", "GRADE_3");
        node.put("unexpected", true);
        node.put("suspected", false);
        node.put("siteId", "site-london-01");
        var patient = node.putObject("patientContext");
        patient.put("hasPriorGrade3OrAbove", true);
        patient.put("hasPriorEscalation", false);
        patient.put("aeCount", 4);

        var features = extractor.extractFeatures(contextWith(node));

        assertThat(features)
                .containsEntry("ctcaeGrade", 3)
                .containsEntry("unexpected", true)
                .containsEntry("suspected", false)
                .containsEntry("siteId", "site-london-01")
                .containsEntry("hasPriorGrade3OrAbove", true)
                .containsEntry("hasPriorEscalation", false)
                .containsEntry("aeCount", 4)
                .hasSize(7);
    }

    @Test
    void extractFeatures_invalidGradeString_omitsGrade() {
        var node = JsonNodeFactory.instance.objectNode();
        node.put("grade", "UNKNOWN");

        var features = extractor.extractFeatures(contextWith(node));

        assertThat(features).doesNotContainKey("ctcaeGrade");
    }

    @Test
    void parseGrade_allValidGrades() {
        assertThat(ClinicalRoutingFeatureExtractor.parseGrade(
                JsonNodeFactory.instance.textNode("GRADE_1"))).isEqualTo(1);
        assertThat(ClinicalRoutingFeatureExtractor.parseGrade(
                JsonNodeFactory.instance.textNode("GRADE_5"))).isEqualTo(5);
    }

    @Test
    void parseGrade_missingNode_returnsZero() {
        assertThat(ClinicalRoutingFeatureExtractor.parseGrade(
                com.fasterxml.jackson.databind.node.MissingNode.getInstance())).isZero();
    }

    @Test
    void extractFeatures_nullNode_returnsEmpty() {
        var ctx = new AgentRoutingContext(UUID.randomUUID(), "safety-monitoring", NullNode.instance, "t", List.of(), null, null);
        assertThat(extractor.extractFeatures(ctx)).isEmpty();
    }

    @Test
    void extractProblem_nullNode_returnsNull() {
        var ctx = new AgentRoutingContext(UUID.randomUUID(), "safety-monitoring", NullNode.instance, "t", List.of(), null, null);
        assertThat(extractor.extractProblem(ctx)).isNull();
    }

    @Test
    void extractProblem_unexpectedGrade3_formatsCorrectly() {
        var node = JsonNodeFactory.instance.objectNode();
        node.put("grade", "GRADE_3");
        node.put("unexpected", true);
        var patient = node.putObject("patientContext");
        patient.put("aeCount", 2);

        assertThat(extractor.extractProblem(contextWith(node)))
                .isEqualTo("Grade 3 AE, unexpected, patient has 2 prior AEs");
    }

    @Test
    void extractProblem_expectedGrade4_formatsCorrectly() {
        var node = JsonNodeFactory.instance.objectNode();
        node.put("grade", "GRADE_4");
        node.put("unexpected", false);
        var patient = node.putObject("patientContext");
        patient.put("aeCount", 0);

        assertThat(extractor.extractProblem(contextWith(node)))
                .isEqualTo("Grade 4 AE, expected, patient has 0 prior AEs");
    }
}
