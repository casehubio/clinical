package io.casehub.clinical.cbr;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.clinical.api.ProtocolAmendmentResolvedEvent;
import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.cbr.TextualCbrCase;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@QuarkusTest
class AmendmentResolutionCbrWriterIntegrationTest {

    @Inject AmendmentResolutionCbrWriter writer;
    @Inject ClinicalCbrService cbrService;

    UUID amendmentId;
    UUID trialId;
    UUID engineCaseId;

    @BeforeEach
    @Transactional
    void setup() {
        amendmentId = UUID.randomUUID();
        trialId = UUID.randomUUID();
        engineCaseId = UUID.randomUUID();

        ProtocolAmendment amendment = new ProtocolAmendment();
        amendment.id = amendmentId;
        amendment.trialId = trialId;
        amendment.engineCaseId = engineCaseId;
        amendment.proposedChange = "Extend enrollment period by 6 months";
        amendment.status = ProtocolAmendmentStatus.APPROVED;
        amendment.supervisorRecommendation = AmendmentRecommendation.PROCEED;
        amendment.amendmentCaseStatus = AmendmentCaseStatus.COMPLETED;
        amendment.tenantId = "default";
        amendment.proposedAt = Instant.now();
        amendment.persist();
    }

    @Test
    void end_to_end_storage_and_retrieval() {
        var event = new ProtocolAmendmentResolvedEvent(
            amendmentId, trialId,
            ProtocolAmendmentStatus.APPROVED,
            AmendmentRecommendation.PROCEED,
            "default"
        );

        writer.onAmendmentResolved(event);

        // Verify CBR case was stored
        var query = CbrQuery.of(
            "default",
            ClinicalCbrDomains.AMENDMENT,
            "clinical-amendment",
            Map.of(),
            10
        ).withProblem("Extend enrollment period by 6 months");

        List<ScoredCbrCase<TextualCbrCase>> results = cbrService.retrieveSimilar(query, TextualCbrCase.class);

        assertThat(results).isNotEmpty();
        ScoredCbrCase<TextualCbrCase> first = results.get(0);
        assertThat(first.cbrCase().problem()).isEqualTo("Extend enrollment period by 6 months");
        assertThat(first.cbrCase().solution()).isEqualTo("PROCEED");
        assertThat(first.cbrCase().outcome()).isEqualTo("APPROVED");
        assertThat(first.cbrCase().confidence()).isEqualTo(1.0);
    }

    @Test
    void idempotent_when_called_twice() {
        var event = new ProtocolAmendmentResolvedEvent(
            amendmentId, trialId,
            ProtocolAmendmentStatus.APPROVED,
            AmendmentRecommendation.PROCEED,
            "default"
        );

        writer.onAmendmentResolved(event);
        writer.onAmendmentResolved(event);

        // Verify only one CBR case exists
        var query = CbrQuery.of(
            "default",
            ClinicalCbrDomains.AMENDMENT,
            "clinical-amendment",
            Map.of(),
            10
        ).withProblem("Extend enrollment period by 6 months");

        List<ScoredCbrCase<TextualCbrCase>> results = cbrService.retrieveSimilar(query, TextualCbrCase.class);
        assertThat(results).hasSize(1);
    }
}
