package io.casehub.clinical.routing;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.api.spi.routing.AgentRoutingContext;

import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ClinicalRoutingFeatureExtractor  {

    private static final Pattern GRADE_PATTERN = Pattern.compile("GRADE_(\\d+)");

    public Map<String, Object> extractFeatures(AgentRoutingContext context) {
        JsonNode root = context.caseContext();
        if (root == null || root.isNull()) {
            return Map.of();
        }

        Map<String, Object> features = new LinkedHashMap<>();

        int grade = parseGrade(root.path("grade"));
        if (grade > 0) {
            features.put("ctcaeGrade", grade);
        }

        putBoolIfPresent(features, "unexpected", root);
        putBoolIfPresent(features, "suspected", root);
        putStringIfPresent(features, "siteId", root);

        JsonNode patient = root.path("patientContext");
        if (!patient.isMissingNode()) {
            putBoolIfPresent(features, "hasPriorGrade3OrAbove", patient);
            putBoolIfPresent(features, "hasPriorEscalation", patient);
            putIntIfPresent(features, "aeCount", patient);
        }

        return Map.copyOf(features);
    }

    public @Nullable String extractProblem(AgentRoutingContext context) {
        JsonNode root = context.caseContext();
        if (root == null || root.isNull()) {
            return null;
        }

        int grade = parseGrade(root.path("grade"));
        if (grade <= 0) {
            return null;
        }

        boolean unexpected = root.path("unexpected").asBoolean(false);
        int aeCount = root.path("patientContext").path("aeCount").asInt(0);

        return String.format("Grade %d AE, %s, patient has %d prior AEs",
                grade, unexpected ? "unexpected" : "expected", aeCount);
    }

    static int parseGrade(JsonNode gradeNode) {
        if (gradeNode.isMissingNode() || !gradeNode.isTextual()) {
            return 0;
        }
        Matcher m = GRADE_PATTERN.matcher(gradeNode.asText());
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static void putBoolIfPresent(Map<String, Object> map, String key, JsonNode parent) {
        JsonNode child = parent.path(key);
        if (!child.isMissingNode() && child.isBoolean()) {
            map.put(key, child.asBoolean());
        }
    }

    private static void putIntIfPresent(Map<String, Object> map, String key, JsonNode parent) {
        JsonNode child = parent.path(key);
        if (!child.isMissingNode() && child.isInt()) {
            map.put(key, child.asInt());
        }
    }

    private static void putStringIfPresent(Map<String, Object> map, String key, JsonNode parent) {
        JsonNode child = parent.path(key);
        if (!child.isMissingNode() && child.isTextual()) {
            map.put(key, child.asText());
        }
    }
}
