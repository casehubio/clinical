package io.casehub.clinical.service;

import io.casehub.eidos.org.api.OrgStructure;
import io.casehub.eidos.org.memory.InMemoryOrgRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalOrgStructureTest {

    private static final String TENANT = "default";
    private InMemoryOrgRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryOrgRegistry();
        ClinicalOrgRegistrar.register(registry, TENANT);
    }

    @Test
    void registersRootUnit() {
        var root = registry.findUnit("clinical-ops", TENANT);
        assertThat(root).isPresent();
        assertThat(root.get().name()).isEqualTo("Clinical Trial Operations");
    }

    @Test
    void registersSafetyTeamWithCapabilities() {
        var safety = registry.findUnit("safety-team", TENANT);
        assertThat(safety).isPresent();
        var caps = safety.get().capabilities().stream()
                .map(c -> c.name())
                .toList();
        assertThat(caps).contains("safety-monitoring", "susar-criteria");
    }

    @Test
    void registersSiteTeam() {
        var site = registry.findUnit("site-team", TENANT);
        assertThat(site).isPresent();
        var caps = site.get().capabilities().stream()
                .map(c -> c.name())
                .toList();
        assertThat(caps).contains("eligibility-screening");
    }

    @Test
    void registersRegulatoryTeam() {
        var reg = registry.findUnit("regulatory-team", TENANT);
        assertThat(reg).isPresent();
        var caps = reg.get().capabilities().stream()
                .map(c -> c.name())
                .toList();
        assertThat(caps).contains("regulatory-submission", "protocol-amendment-advisor");
    }

    @Test
    void safetyTeamIsChildOfRoot() {
        var children = registry.childUnits("clinical-ops", TENANT);
        assertThat(children).anyMatch(u -> u.unitId().equals("safety-team"));
    }

    @Test
    void registersSupervisionRelationships() {
        var supervisors = registry.supervisors("safety-team", TENANT);
        assertThat(supervisors).isNotEmpty();
    }

    @Test
    void registersEscalationEdges() {
        var escalation = registry.escalationPath("site-team", TENANT);
        assertThat(escalation).isNotEmpty();
    }
}
