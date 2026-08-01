package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.api.spi.ProtocolAmendmentAdvisor;
import io.casehub.clinical.api.spi.ProtocolAmendmentContext;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class ProtocolAmendmentAdvisorIntegrationTest {

    @Inject ProtocolAmendmentAdvisor advisor;
    @Inject FixedCurrentPrincipal principal;
    @InjectMock AgentProvider agentProvider;

    private UUID trialId;

    @BeforeEach
    @Transactional
    void setUp() {
        AdverseEvent.deleteAll();
        ProtocolAmendment.deleteAll();
        PatientEnrollment.deleteAll();
        TrialSite.deleteAll();
        ClinicalTrial.deleteAll();

        trialId = UUID.randomUUID();
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.protocolId = "AMEND-TEST";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "TestPharma";
        trial.status = TrialStatus.ACTIVE;
        trial.tenantId = principal.tenancyId();
        trial.persist();

        UUID siteId = UUID.randomUUID();
        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = "pi-test";
        site.tenantId = principal.tenancyId();
        site.persist();

        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = UUID.randomUUID();
        enrollment.siteId = siteId;
        enrollment.patientId = "P001";
        enrollment.tenantId = principal.tenancyId();
        enrollment.persist();

        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = enrollment.id;
        ae.grade = CtcaeGrade.GRADE_4;
        ae.eventType = "Neutropenia";
        ae.unexpected = true;
        ae.suspected = true;
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.tenantId = principal.tenancyId();
        ae.persist();

        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(
                        new AgentEvent.TextDelta("{\"recommendation\": \"REFER_TO_DSMB\", \"reasoning\": \"Grade 4 AE present\"}")));
    }

    @Test
    void cdi_displacement_uses_llm_advisor() {
        assertThat(advisor).isInstanceOf(LlmProtocolAmendmentAdvisor.class);
    }

    @Test
    void advise_with_enriched_context_returns_recommendation() {
        ProtocolAmendmentContext ctx = new ProtocolAmendmentContext(
                UUID.randomUUID(), trialId, "Add imaging endpoint",
                Map.of("trialPhase", "PHASE_III", "totalAdverseEvents", 1L, "grade3PlusCount", 1L, "hasGrade5", false));

        AmendmentRecommendation rec = advisor.advise(ctx);
        assertThat(rec).isEqualTo(AmendmentRecommendation.REFER_TO_DSMB);
    }

    @Test
    void advise_prompt_includes_trial_context() {
        ProtocolAmendmentContext ctx = new ProtocolAmendmentContext(
                UUID.randomUUID(), trialId, "Change primary endpoint",
                Map.of("trialPhase", "PHASE_III", "totalAdverseEvents", 1L, "grade3PlusCount", 1L, "hasGrade5", false, "priorAmendmentCount", 2));

        advisor.advise(ctx);

        ArgumentCaptor<AgentSessionConfig> captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(captor.capture());
        String userPrompt = captor.getValue().userPrompt();
        assertThat(userPrompt).contains("Change primary endpoint");
        assertThat(userPrompt).contains("PHASE_III");
        assertThat(userPrompt).contains("1");
    }
}
