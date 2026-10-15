package io.casehub.clinical.playbook;

import io.casehub.pages.playbook.PlaybookContentParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClinicalPlaybookResourceTest {

    @Test
    void bundled_demo_uses_the_current_playbook_format() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "META-INF/playbooks/clinical-trial-demo.playbook.yaml")) {
            assertNotNull(stream, "The clinical demo must be available to Pages playbook discovery");
            var playbook = PlaybookContentParser.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("clinical-trial-demo", playbook.playbook());
            assertEquals(List.of("Trial Setup", "Accountability in Action", "AI Governance", "The Proof"),
                    playbook.chapters().stream().map(chapter -> chapter.label()).toList());

            var create = playbook.allSteps().stream()
                    .filter(step -> "create-trial".equals(PlaybookContentParser.deriveStepName(step, 0)))
                    .findFirst().orElseThrow();
            assertEquals("createTrial", create.action());
            assertEquals("ONCO-2026-DEMO", create.params().get("protocolId"));
            assertEquals(100, create.params().get("targetEnrollment"));
            assertEquals("server", create.decorator("target"));

            var activate = playbook.allSteps().stream()
                    .filter(step -> "activateTrial".equals(step.action()))
                    .findFirst().orElseThrow();
            assertEquals("${create-trial.trialId}", activate.params().get("trialId"));

            var enroll = playbook.allSteps().stream()
                    .filter(step -> "enrollPatient".equals(step.action()))
                    .findFirst().orElseThrow();
            assertEquals("bulk", enroll.decorator("mode"));
            assertEquals(5, ((List<?>) enroll.params().get("items")).size());
            assertFalse(playbook.allSteps().stream()
                    .anyMatch(step -> List.of("name", "commands").contains(step.action())));
        }
    }
}
